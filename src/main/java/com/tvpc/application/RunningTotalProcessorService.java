package com.tvpc.application;

import com.tvpc.domain.Settlement;
import com.tvpc.domain.SettlementEvent;
import com.tvpc.domain.ports.inbound.RunningTotalProcessorUseCase;
import com.tvpc.domain.ports.outbound.ExchangeRateRepositoryPort;
import com.tvpc.domain.ports.outbound.RunningTotalRepositoryPort;
import com.tvpc.domain.ports.outbound.SettlementRepositoryPort;
import com.tvpc.domain.ports.outbound.TransactionManagerPort;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Application service for processing settlement events.
 * Implements RunningTotalProcessorUseCase using only port interfaces.
 * Handles event-driven running total calculations.
 */
public class RunningTotalProcessorService implements RunningTotalProcessorUseCase {

    private final SettlementRepositoryPort settlementRepository;
    private final RunningTotalRepositoryPort runningTotalRepository;
    private final ExchangeRateRepositoryPort exchangeRateRepository;
    private final TransactionManagerPort transactionManager;

    public RunningTotalProcessorService(
            SettlementRepositoryPort settlementRepository,
            RunningTotalRepositoryPort runningTotalRepository,
            ExchangeRateRepositoryPort exchangeRateRepository,
            TransactionManagerPort transactionManager
    ) {
        this.settlementRepository = settlementRepository;
        this.runningTotalRepository = runningTotalRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.transactionManager = transactionManager;
    }

    @Override
    public Future<Void> processEvent(SettlementEvent event) {
        // Execute in transaction
        return transactionManager.executeInTransaction(connection -> {
            Promise<Void> promise = Promise.promise();

            // Get all settlements for the group (with filtering)
            settlementRepository.findByGroupWithFilters(
                    event.getPts(),
                    event.getProcessingEntity(),
                    event.getCounterpartyId(),
                    event.getValueDate().toString(),
                    event.getSeqId()
            ).compose(settlements -> {
                if (settlements.isEmpty()) {
                    // No settlements to calculate - set total to 0
                    return runningTotalRepository.updateRunningTotal(
                            event.getPts(),
                            event.getProcessingEntity(),
                            event.getCounterpartyId(),
                            event.getValueDate(),
                            BigDecimal.ZERO,
                            event.getSeqId()
                    );
                }

                // Calculate total in USD
                return processSettlementsRecursively(
                        settlements,
                        0,
                        BigDecimal.ZERO,
                        event
                );

            }).onSuccess(v -> promise.complete())
              .onFailure(promise::fail);

            return promise.future();
        });
    }

    /**
     * Recursive processing of settlements to handle async exchange rate lookups
     */
    private Future<Void> processSettlementsRecursively(
            List<Settlement> settlements,
            int index,
            BigDecimal runningTotal,
            SettlementEvent event
    ) {
        if (index >= settlements.size()) {
            // All settlements processed, update running total
            return runningTotalRepository.updateRunningTotal(
                    event.getPts(),
                    event.getProcessingEntity(),
                    event.getCounterpartyId(),
                    event.getValueDate(),
                    runningTotal,
                    event.getSeqId()
            );
        }

        Settlement settlement = settlements.get(index);

        // Get exchange rate
        return exchangeRateRepository.getRate(settlement.getCurrency())
                .compose(rateOpt -> {
                    if (rateOpt.isEmpty()) {
                        return Future.failedFuture("No exchange rate found for currency: " + settlement.getCurrency());
                    }

                    BigDecimal rate = rateOpt.get();
                    BigDecimal usdAmount = settlement.getAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP);

                    // Continue with next settlement
                    return processSettlementsRecursively(
                            settlements,
                            index + 1,
                            runningTotal.add(usdAmount),
                            event
                    );
                });
    }
}
