package com.tvpc.domain.ports.inbound;

import io.vertx.core.Future;

import java.time.LocalDate;

/**
 * Inbound port for manual recalculation operations.
 * Admin-only operations for recalculating running totals.
 */
public interface ManualRecalculationUseCase {

    /**
     * Recalculates running total for a specific group.
     *
     * @param pts The trading system identifier
     * @param processingEntity The processing entity
     * @param counterpartyId The counterparty identifier
     * @param valueDate The value date
     * @param reason The reason for recalculation
     * @param userId The user performing recalculation
     * @return Void future
     */
    Future<Void> recalculateGroup(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate,
            String reason,
            String userId
    );

    /**
     * Recalculates running totals for multiple groups based on criteria.
     *
     * @param pts The trading system identifier (optional)
     * @param processingEntity The processing entity (optional)
     * @param valueDateFrom Start value date (optional)
     * @param valueDateTo End value date (optional)
     * @param reason The reason for recalculation
     * @param userId The user performing recalculation
     * @return Void future
     */
    Future<Void> recalculateByCriteria(
            String pts,
            String processingEntity,
            LocalDate valueDateFrom,
            LocalDate valueDateTo,
            String reason,
            String userId
    );
}
