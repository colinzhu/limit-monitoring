package com.tvpc.domain.port.repository;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlConnection;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Repository port for Running Total operations
 * This is a domain port (interface) that defines the contract for running total persistence.
 * Infrastructure layer will provide the implementation.
 */
public interface RunningTotalRepository {

    /**
     * Update or insert running total for a group
     * @param pts Primary Trading System
     * @param processingEntity Business unit
     * @param counterpartyId Counterparty identifier
     * @param valueDate Settlement value date
     * @param runningTotal Calculated total in USD
     * @param refId Sequence ID used for calculation
     * @param connection Database connection
     * @return Future indicating completion
     */
    Future<Void> updateRunningTotal(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate,
            BigDecimal runningTotal,
            Long refId,
            SqlConnection connection
    );

    /**
     * Get current running total for a group
     * @param pts Primary Trading System
     * @param processingEntity Business unit
     * @param counterpartyId Counterparty identifier
     * @param valueDate Settlement value date
     * @return Future with optional running total
     */
    Future<java.util.Optional<RunningTotal>> getRunningTotal(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate
    );

    /**
     * Calculate and save running total in a single SQL operation
     * Combines query, calculation, and update into one database call
     * @param pts Primary Trading System
     * @param processingEntity Business unit
     * @param counterpartyId Counterparty identifier
     * @param valueDate Settlement value date
     * @param maxSeqId Maximum sequence ID to include
     * @param connection Database connection
     * @return Future indicating completion
     */
    Future<Void> calculateAndSaveRunningTotal(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate,
            Long maxSeqId,
            SqlConnection connection
    );

    /**
     * Inner class for running total data
     */
    @Value
    class RunningTotal {
        BigDecimal total;
        Long refId;
    }
}


