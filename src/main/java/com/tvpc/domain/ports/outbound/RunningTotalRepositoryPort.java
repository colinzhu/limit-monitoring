package com.tvpc.domain.ports.outbound;

import com.tvpc.domain.RunningTotal;
import io.vertx.core.Future;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Outbound port for running total persistence operations.
 */
public interface RunningTotalRepositoryPort {

    /**
     * Updates or creates a running total for a group.
     * Uses REF_ID for idempotency and prevents concurrent updates.
     *
     * @param pts The trading system identifier
     * @param processingEntity The processing entity
     * @param counterpartyId The counterparty identifier
     * @param valueDate The value date
     * @param runningTotal The calculated running total in USD
     * @param refId The settlement sequence ID used for calculation
     * @return Void future
     */
    Future<Void> updateRunningTotal(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate,
            BigDecimal runningTotal,
            Long refId
    );

    /**
     * Retrieves a running total for a group.
     *
     * @param pts The trading system identifier
     * @param processingEntity The processing entity
     * @param counterpartyId The counterparty identifier
     * @param valueDate The value date
     * @return Optional containing running total, or empty if not found
     */
    Future<Optional<RunningTotal>> getRunningTotal(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate
    );
}
