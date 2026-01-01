package com.tvpc.domain.ports.inbound;

import com.tvpc.domain.SettlementStatus;
import io.vertx.core.Future;

/**
 * Inbound port for status query operations.
 */
public interface StatusQueryUseCase {

    /**
     * Gets the current status of a settlement.
     * Status is calculated on-demand based on:
     * - Running total vs limit
     * - Workflow state
     * - Settlement properties
     *
     * @param settlementId The settlement identifier
     * @param settlementVersion The settlement version (optional, uses latest if null)
     * @return The calculated settlement status
     */
    Future<SettlementStatus> getSettlementStatus(String settlementId, Long settlementVersion);

    /**
     * Gets the current status for multiple settlements.
     *
     * @param settlementId The settlement identifier
     * @return List of statuses for all versions
     */
    Future<java.util.List<SettlementStatus>> getSettlementStatusHistory(String settlementId);
}
