package com.tvpc.application.usecase;

import com.tvpc.application.dto.SettlementRequest;
import com.tvpc.application.dto.ValidationResult;
import com.tvpc.application.port.inbound.ProcessSettlementUseCase;
import com.tvpc.application.port.outbound.*;
import com.tvpc.domain.event.SettlementEvent;
import com.tvpc.domain.model.Settlement;
import com.tvpc.domain.model.BusinessStatus;
import com.tvpc.domain.model.SettlementDirection;
import com.tvpc.domain.model.SettlementType;
import io.vertx.core.Future;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.SqlConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Use case implementation for settlement processing
 * Orchestrates the 5-step ingestion flow
 */
public class ProcessSettlementUseCaseImpl implements ProcessSettlementUseCase {
    private static final Logger log = LoggerFactory.getLogger(ProcessSettlementUseCaseImpl.class);

    private final SettlementRepository settlementRepository;
    private final RunningTotalRepository runningTotalRepository;
    private final ActivityRepository activityRepository;
    private final ConfigurationRepository configurationRepository;
    private final JDBCPool jdbcPool;

    public ProcessSettlementUseCaseImpl(
            SettlementRepository settlementRepository,
            RunningTotalRepository runningTotalRepository,
            ActivityRepository activityRepository,
            ConfigurationRepository configurationRepository,
            JDBCPool jdbcPool
    ) {
        this.settlementRepository = settlementRepository;
        this.runningTotalRepository = runningTotalRepository;
        this.activityRepository = activityRepository;
        this.configurationRepository = configurationRepository;
        this.jdbcPool = jdbcPool;
    }

    @Override
    public Future<Long> processSettlement(SettlementRequest request) {
        log.info("Processing settlement: {}", request.getSettlementId());

        // Step 0: Validate
        ValidationResult validation = validate(request);
        if (!validation.isValid()) {
            log.warn("Validation failed for settlement {}: {}", request.getSettlementId(), validation.getErrors());
            return Future.failedFuture(new IllegalArgumentException("Validation failed: " + validation.getErrors()));
        }

        // Convert to domain entity
        Settlement settlement = convertToDomain(request);

        // Execute all steps within a single transaction
        return jdbcPool.withTransaction(connection -> executeIngestionSteps(connection, settlement));
    }

    /**
     * Execute the 5-step ingestion flow within a transaction
     */
    private Future<Long> executeIngestionSteps(SqlConnection connection, Settlement settlement) {
        // Step 1: Save Settlement
        return saveSettlement(settlement, connection)
                .compose(seqId -> {
                    log.debug("Step 1: Saved settlement with seqId: {}", seqId);

                    // Step 2: Mark Old Versions
                    return markOldVersions(settlement, connection)
                            .map(seqId);
                })
                .compose(seqId -> {
                    log.debug("Step 2: Marked old versions");

                    // Step 3: Detect Counterparty Changes
                    return detectCounterpartyChange(settlement, seqId, connection)
                            .map(oldCounterparty -> {
                                log.debug("Step 3: Detected counterparty change: {}", oldCounterparty);
                                return new CounterpartyChangeResult(seqId, oldCounterparty);
                            });
                })
                .compose(result -> {
                    log.debug("Step 4: Generate events and calculate running totals");

                    // Step 4 & 5: Generate events and calculate running totals synchronously
                    List<SettlementEvent> events = generateEvents(settlement, result.seqId, result.oldCounterparty);

                    // Calculate running totals for all events
                    Future<Void> runningTotalFuture = Future.succeededFuture();

                    for (SettlementEvent event : events) {
                        runningTotalFuture = runningTotalFuture.compose(v ->
                            calculateRunningTotalForGroup(
                                    event.getPts(),
                                    event.getProcessingEntity(),
                                    event.getCounterpartyId(),
                                    event.getValueDate(),
                                    event.getRefId(),
                                    connection
                            )
                        );
                    }

                    return runningTotalFuture.map(result.seqId);
                })
                .onSuccess(seqId -> log.info("Successfully processed settlement {} with seqId: {}", settlement.getSettlementId(), seqId))
                .onFailure(error -> log.error("Failed to process settlement {}: {}", settlement.getSettlementId(), error.getMessage()));
    }

    /**
     * Step 1: Save Settlement
     */
    private Future<Long> saveSettlement(Settlement settlement, SqlConnection connection) {
        return settlementRepository.save(settlement, connection)
                .onSuccess(seqId -> log.debug("saveSettlement returned seqId: {}", seqId))
                .onFailure(error -> log.error("saveSettlement failed: {}", error.getMessage()));
    }

    /**
     * Step 2: Mark Old Versions
     */
    private Future<Void> markOldVersions(Settlement settlement, SqlConnection connection) {
        return settlementRepository.markOldVersions(
                settlement.getSettlementId(),
                settlement.getPts(),
                settlement.getProcessingEntity(),
                connection
        );
    }

    /**
     * Step 3: Detect Counterparty Changes
     */
    private Future<Optional<String>> detectCounterpartyChange(Settlement settlement, Long seqId, SqlConnection connection) {
        return settlementRepository.findPreviousCounterparty(
                settlement.getSettlementId(),
                settlement.getPts(),
                settlement.getProcessingEntity(),
                seqId,
                connection
        );
    }

    /**
     * Step 4: Generate Events
     */
    private List<SettlementEvent> generateEvents(Settlement settlement, Long seqId, Optional<String> oldCounterparty) {
        List<SettlementEvent> events = new ArrayList<>();

        // Default: 1 event for current group
        SettlementEvent currentEvent = new SettlementEvent(
                settlement.getPts(),
                settlement.getProcessingEntity(),
                settlement.getCounterpartyId(),
                settlement.getValueDate(),
                seqId
        );
        events.add(currentEvent);

        // If counterparty changed, also trigger recalculation for old group
        if (oldCounterparty.isPresent() && !oldCounterparty.get().equals(settlement.getCounterpartyId())) {
            SettlementEvent oldEvent = new SettlementEvent(
                    settlement.getPts(),
                    settlement.getProcessingEntity(),
                    oldCounterparty.get(),
                    settlement.getValueDate(),
                    seqId
            );
            events.add(oldEvent);
            log.info("Counterparty changed from {} to {}, triggering recalculation for both groups",
                    oldCounterparty.get(), settlement.getCounterpartyId());
        }

        return events;
    }

    /**
     * Step 5: Calculate Running Total for a Group
     */
    private Future<Void> calculateRunningTotalForGroup(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate,
            Long seqId,
            SqlConnection connection
    ) {
        log.debug("Calculating running total for group (pts={}, pe={}, cp={}, vd={}, seqId={})",
                pts, processingEntity, counterpartyId, valueDate, seqId);

        return runningTotalRepository.calculateAndSaveRunningTotal(
                pts,
                processingEntity,
                counterpartyId,
                valueDate,
                seqId,
                connection
        );
    }

    /**
     * Validate settlement request
     */
    private ValidationResult validate(SettlementRequest request) {
        List<String> errors = new ArrayList<>();

        // Required fields
        if (request.getSettlementId() == null || request.getSettlementId().trim().isEmpty()) {
            errors.add("settlementId is required");
        }
        if (request.getSettlementVersion() == null) {
            errors.add("settlementVersion is required");
        }
        if (request.getPts() == null || request.getPts().trim().isEmpty()) {
            errors.add("pts is required");
        }
        if (request.getProcessingEntity() == null || request.getProcessingEntity().trim().isEmpty()) {
            errors.add("processingEntity is required");
        }
        if (request.getCounterpartyId() == null || request.getCounterpartyId().trim().isEmpty()) {
            errors.add("counterpartyId is required");
        }
        if (request.getValueDate() == null || request.getValueDate().trim().isEmpty()) {
            errors.add("valueDate is required");
        }
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            errors.add("currency is required");
        }
        if (request.getAmount() == null) {
            errors.add("amount is required");
        }
        if (request.getBusinessStatus() == null || request.getBusinessStatus().trim().isEmpty()) {
            errors.add("businessStatus is required");
        }
        if (request.getDirection() == null || request.getDirection().trim().isEmpty()) {
            errors.add("direction is required");
        }
        if (request.getSettlementType() == null || request.getSettlementType().trim().isEmpty()) {
            errors.add("settlementType is required");
        }

        // Validate enums
        if (request.getBusinessStatus() != null && !BusinessStatus.isValid(request.getBusinessStatus())) {
            errors.add("invalid businessStatus: " + request.getBusinessStatus());
        }
        if (request.getDirection() != null && !SettlementDirection.isValid(request.getDirection())) {
            errors.add("invalid direction: " + request.getDirection());
        }
        if (request.getSettlementType() != null && !SettlementType.isValid(request.getSettlementType())) {
            errors.add("invalid settlementType: " + request.getSettlementType());
        }

        // Validate amount
        if (request.getAmount() != null && request.getAmount() < 0) {
            errors.add("amount cannot be negative");
        }

        // Validate date format
        try {
            if (request.getValueDate() != null) {
                LocalDate.parse(request.getValueDate());
            }
        } catch (Exception e) {
            errors.add("invalid valueDate format: " + request.getValueDate());
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid();
        } else {
            return ValidationResult.invalid(errors);
        }
    }

    /**
     * Convert DTO to domain entity
     */
    private Settlement convertToDomain(SettlementRequest request) {
        return Settlement.builder()
                .settlementId(request.getSettlementId())
                .settlementVersion(request.getSettlementVersion())
                .pts(request.getPts())
                .processingEntity(request.getProcessingEntity())
                .counterpartyId(request.getCounterpartyId())
                .valueDate(LocalDate.parse(request.getValueDate()))
                .currency(request.getCurrency())
                .amount(java.math.BigDecimal.valueOf(request.getAmount()))
                .businessStatus(BusinessStatus.fromValue(request.getBusinessStatus()))
                .direction(SettlementDirection.fromValue(request.getDirection()))
                .settlementType(SettlementType.fromValue(request.getSettlementType()))
                .isOld(false)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }

    // Helper class for counterparty change result
    private static class CounterpartyChangeResult {
        final Long seqId;
        final Optional<String> oldCounterparty;

        CounterpartyChangeResult(Long seqId, Optional<String> oldCounterparty) {
            this.seqId = seqId;
            this.oldCounterparty = oldCounterparty;
        }
    }
}
