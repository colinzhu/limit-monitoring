package com.tvpc.application.port.out;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlConnection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Output port for running total persistence operations
 */
public interface RunningTotalRepository {

    /**
     * Update or insert running total for a group
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
     */
    Future<Optional<RunningTotalData>> getRunningTotal(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate
    );

    /**
     * Calculate and save running total in a single SQL operation
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
     * Data class for running total
     */
    record RunningTotalData(BigDecimal total, Long refId) {}
}
