package com.tvpc.application;

import com.tvpc.domain.Settlement;
import com.tvpc.domain.SettlementEvent;
import com.tvpc.domain.ports.inbound.SettlementIngestionUseCase;
import com.tvpc.domain.ports.outbound.*;
import com.tvpc.domain.services.SettlementValidator;
import com.tvpc.dto.SettlementRequest;
import com.tvpc.dto.ValidationResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Application service for settlement ingestion.
 * Implements SettlementIngestionUseCase using only port interfaces.
 * No infrastructure dependencies - all operations through ports.
 */
public class SettlementIngestionService implements SettlementIngestionUseCase {

    private final SettlementValidator validator;
    private final SettlementRepositoryPort settlementRepository;
    private final RunningTotalRepositoryPort runningTotalRepository;
    private final ExchangeRateRepositoryPort exchangeRateRepository;
    private final EventPublisherPort eventPublisher;
    private final TransactionManagerPort transactionManager;
    private final ConfigurationServicePort configService;

    public SettlementIngestionService(
            SettlementValidator validator,
            SettlementRepositoryPort settlementRepository,
            RunningTotalRepositoryPort runningTotalRepository,
            ExchangeRateRepositoryPort exchangeRateRepository,
            EventPublisherPort eventPublisher,
            TransactionManagerPort transactionManager,
            ConfigurationServicePort configService
    ) {
        this.validator = validator;
        this.settlementRepository = settlementRepository;
        this.runningTotalRepository = runningTotalRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.eventPublisher = eventPublisher;
        this.transactionManager = transactionManager;
        this.configService = configService;
    }

    @Override
    public Future<Long> processSettlement(SettlementRequest request) {
        // Step 0: Validate
        ValidationResult validation = validator.validate(request);
        if (!validation.isValid()) {
            return Future.failedFuture(new IllegalArgumentException("Validation failed: " + validation.getErrors()));
        }

        // Convert to domain object
        Settlement settlement = convertToDomain(request);

        // Execute all steps in a transaction
        return transactionManager.executeInTransaction(connection -> {
            Promise<Long> promise = Promise.promise();

            // Step 1: Save Settlement
            settlementRepository.save(settlement)
                    .compose(seqId -> {
                        settlement.setId(seqId);

                        // Step 2: Mark Old Versions
                        return settlementRepository.markOldVersions(
                                settlement.getSettlementId(),
                                settlement.getPts(),
                                settlement.getProcessingEntity()
                        ).map(seqId);
                    })
                    .compose(seqId -> {
                        // Step 3: Detect Counterparty Changes
                        return settlementRepository.findPreviousCounterparty(
                                settlement.getSettlementId(),
                                settlement.getPts(),
                                settlement.getProcessingEntity(),
                                seqId
                        ).map(oldCounterparty -> new CounterpartyChangeResult(seqId, oldCounterparty));
                    })
                    .compose(result -> {
                        // Step 4: Generate Events
                        generateEvents(settlement, result.seqId, result.oldCounterparty);

                        // Step 5: Calculate Running Total for current group
                        return calculateRunningTotal(settlement, result.seqId)
                                .compose(v -> {
                                    // If counterparty changed, also calculate for old group
                                    if (result.oldCounterparty.isPresent() &&
                                        !result.oldCounterparty.get().equals(settlement.getCounterpartyId())) {
                                        return calculateRunningTotalForGroup(
                                                settlement.getPts(),
                                                settlement.getProcessingEntity(),
                                                result.oldCounterparty.get(),
                                                settlement.getValueDate(),
                                                result.seqId
                                        );
                                    }
                                    return Future.succeededFuture();
                                })
                                .map(result.seqId);
                    })
                    .onSuccess(promise::complete)
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    // Step 4: Generate Events
    private void generateEvents(Settlement settlement, Long seqId, Optional<String> oldCounterparty) {
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
        }

        // Publish events
        eventPublisher.publishMultiple(events);
    }

    // Step 5: Calculate Running Total for current group
    private Future<Void> calculateRunningTotal(Settlement settlement, Long seqId) {
        return calculateRunningTotalForGroup(
                settlement.getPts(),
                settlement.getProcessingEntity(),
                settlement.getCounterpartyId(),
                settlement.getValueDate(),
                seqId
        );
    }

    // Step 5 helper: Calculate running total for a specific group
    private Future<Void> calculateRunningTotalForGroup(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate,
            Long seqId
    ) {
        return settlementRepository.findByGroupWithFilters(
                pts,
                processingEntity,
                counterpartyId,
                valueDate.toString(),
                seqId
        ).compose(settlements -> {
            if (settlements.isEmpty()) {
                // No settlements - set total to 0
                return runningTotalRepository.updateRunningTotal(
                        pts,
                        processingEntity,
                        counterpartyId,
                        valueDate,
                        BigDecimal.ZERO,
                        seqId
                );
            }

            // Process settlements to calculate total
            return processSettlementsRecursively(settlements, 0, BigDecimal.ZERO, pts, processingEntity, counterpartyId, valueDate, seqId);
        });
    }

    private Future<Void> processSettlementsRecursively(
            List<Settlement> settlements,
            int index,
            BigDecimal runningTotal,
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate,
            Long seqId
    ) {
        if (index >= settlements.size()) {
            // All done, update running total
            return runningTotalRepository.updateRunningTotal(
                    pts,
                    processingEntity,
                    counterpartyId,
                    valueDate,
                    runningTotal,
                    seqId
            );
        }

        Settlement s = settlements.get(index);

        return exchangeRateRepository.getRate(s.getCurrency())
                .compose(rateOpt -> {
                    if (rateOpt.isEmpty()) {
                        return Future.failedFuture("No exchange rate found for currency: " + s.getCurrency());
                    }

                    BigDecimal rate = rateOpt.get();
                    BigDecimal usdAmount = s.getAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP);

                    // Continue with next settlement
                    return processSettlementsRecursively(
                            settlements,
                            index + 1,
                            runningTotal.add(usdAmount),
                            pts,
                            processingEntity,
                            counterpartyId,
                            valueDate,
                            seqId
                    );
                });
    }

    private Settlement convertToDomain(SettlementRequest request) {
        return new Settlement(
                request.getSettlementId(),
                request.getSettlementVersion(),
                request.getPts(),
                request.getProcessingEntity(),
                request.getCounterpartyId(),
                LocalDate.parse(request.getValueDate()),
                request.getCurrency(),
                request.getAmount(),
                com.tvpc.domain.BusinessStatus.fromValue(request.getBusinessStatus()),
                com.tvpc.domain.SettlementDirection.fromValue(request.getDirection()),
                com.tvpc.domain.SettlementType.fromValue(request.getSettlementType())
        );
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
