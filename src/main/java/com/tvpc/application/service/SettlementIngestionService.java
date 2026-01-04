package com.tvpc.application.service;

import com.tvpc.application.dto.SettlementRequest;
import com.tvpc.application.dto.SettlementResponse;
import com.tvpc.application.dto.ValidationResult;
import com.tvpc.application.port.incoming.SettlementIngestionUseCase;
import com.tvpc.domain.model.BusinessStatus;
import com.tvpc.domain.model.Settlement;
import com.tvpc.domain.model.SettlementDirection;
import com.tvpc.domain.model.SettlementEvent;
import com.tvpc.domain.model.SettlementType;
import com.tvpc.domain.port.configuration.ConfigurationService;
import com.tvpc.domain.port.messaging.EventPublisher;
import com.tvpc.domain.port.repository.ActivityRepository;
import com.tvpc.domain.port.repository.RunningTotalRepository;
import com.tvpc.domain.port.repository.SettlementRepository;
import com.tvpc.domain.service.SettlementValidator;
import io.vertx.core.Future;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.SqlConnection;
import lombok.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Application service for settlement ingestion - implements the 5-step flow
 * This is the use case implementation that orchestrates domain objects and ports.
 */
public class SettlementIngestionService implements SettlementIngestionUseCase {
    private static final Logger log = LoggerFactory.getLogger(SettlementIngestionService.class);

    private final SettlementValidator validator;
    private final SettlementRepository settlementRepository;
    private final RunningTotalRepository runningTotalRepository;
    private final ActivityRepository activityRepository;
    private final EventPublisher eventPublisher;
    private final JDBCPool jdbcPool;
    private final ConfigurationService configurationService;

    public SettlementIngestionService(
            SettlementValidator validator,
            SettlementRepository settlementRepository,
            RunningTotalRepository runningTotalRepository,
            ActivityRepository activityRepository,
            EventPublisher eventPublisher,
            JDBCPool jdbcPool,
            ConfigurationService configurationService
    ) {
        this.validator = validator;
        this.settlementRepository = settlementRepository;
        this.runningTotalRepository = runningTotalRepository;
        this.activityRepository = activityRepository;
        this.eventPublisher = eventPublisher;
        this.jdbcPool = jdbcPool;
        this.configurationService = configurationService;
    }

    @Override
    public Future<SettlementResponse> processSettlement(SettlementRequest request) {
        log.info("Processing settlement: {}", request.getSettlementId());

        // Step 0: Validate
        ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            log.warn("Validation failed for settlement {}: {}", request.getSettlementId(), validation.getErrors());
            return Future.succeededFuture(SettlementResponse.error("Validation failed", validation.getErrors()));
        }

        // Convert to domain object
        Settlement settlement = convertToDomain(request);

        return jdbcPool.withTransaction(connection -> executeIngestionSteps(connection, settlement))
                .map(seqId -> {
                    log.info("Successfully processed settlement {} with seqId: {}", settlement.getSettlementId(), seqId);
                    return SettlementResponse.success("Settlement processed successfully", seqId);
                })
                .onFailure(error -> log.error("Failed to process settlement {}: {}", settlement.getSettlementId(), error.getMessage()))
                .recover(error -> Future.succeededFuture(SettlementResponse.error("Processing failed: " + error.getMessage())));
    }

    @Override
    public Future<SettlementResponse> validateSettlement(SettlementRequest request) {
        ValidationResult validation = validator.validate(request);
        if (validation.isValid()) {
            return Future.succeededFuture(SettlementResponse.success("Validation passed", null));
        } else {
            return Future.succeededFuture(SettlementResponse.error("Validation failed", validation.getErrors()));
        }
    }

    /**
     * Execute the 5-step ingestion flow
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

                    // Step 4 & 5: Generate events and directly calculate running totals
                    // Build event list (1 or 2 events depending on counterparty change)
                    List<SettlementEvent> events = generateEvents(settlement, result.seqId, result.oldCounterparty);

                    // Publish events (for audit/traceability)
                    eventPublisher.publish(events);

                    // Step 5: Loop through events and calculate running totals synchronously
                    // This replaces event dispatch - all calculation happens within the transaction
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
                });
    }

    // Step 1: Save Settlement
    private Future<Long> saveSettlement(Settlement settlement, SqlConnection connection) {
        return settlementRepository.save(settlement, connection)
                .onSuccess(seqId -> log.debug("saveSettlement returned seqId: {}", seqId))
                .onFailure(error -> log.error("saveSettlement failed: {}", error.getMessage()));
    }

    // Step 2: Mark Old Versions
    private Future<Void> markOldVersions(Settlement settlement, SqlConnection connection) {
        return settlementRepository.markOldVersions(
                settlement.getSettlementId(),
                settlement.getPts(),
                settlement.getProcessingEntity(),
                connection
        );
    }

    // Step 3: Detect Counterparty Changes
    private Future<Optional<String>> detectCounterpartyChange(Settlement settlement, Long seqId, SqlConnection connection) {
        return settlementRepository.findPreviousCounterparty(
                settlement.getSettlementId(),
                settlement.getPts(),
                settlement.getProcessingEntity(),
                seqId,
                connection
        );
    }

    // Step 4: Generate Events (returns list for synchronous processing)
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

    // Step 5: Calculate running total for a specific group
    // OPTIMIZED: Uses single SQL to calculate and save running total
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

        // OPTIMIZED: Single database operation combines query, calculation, and update
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
     * Convert DTO to domain object
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
                .amount(request.getAmount())
                .businessStatus(BusinessStatus.fromValue(request.getBusinessStatus()))
                .direction(SettlementDirection.fromValue(request.getDirection()))
                .settlementType(SettlementType.fromValue(request.getSettlementType()))
                .isOld(false)
                .build();
    }

    // Helper class for counterparty change result
    @Value
    private static class CounterpartyChangeResult {
        Long seqId;
        Optional<String> oldCounterparty;
    }
}
