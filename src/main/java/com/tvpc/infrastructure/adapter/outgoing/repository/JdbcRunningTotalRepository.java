package com.tvpc.infrastructure.adapter.outgoing.repository;

import com.tvpc.domain.port.repository.RunningTotalRepository;
import com.tvpc.domain.port.repository.RunningTotalRepository.RunningTotal;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JDBC implementation of RunningTotalRepository
 */
@Slf4j
public class JdbcRunningTotalRepository implements RunningTotalRepository {

    private final SqlClient sqlClient;

    public JdbcRunningTotalRepository(SqlClient sqlClient) {
        this.sqlClient = sqlClient;
    }

    @Override
    public Future<Void> updateRunningTotal(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate,
            BigDecimal runningTotal,
            Long refId,
            SqlConnection connection
    ) {
        LocalDateTime now = LocalDateTime.now();

        // Log the parameters for debugging
        log.debug("updateRunningTotal: pts={}, pe={}, cp={}, vd={}, total={}, refId={}",
                pts, processingEntity, counterpartyId, valueDate, runningTotal, refId);

        // Use MERGE for Oracle (UPSERT pattern)
        // Parameters:
        // 1-6: For USING SELECT (PTS, PE, CP, VD, RUNNING_TOTAL, REF_ID)
        // 7: For WHEN MATCHED UPDATE (UPDATE_TIME) - only if REF_ID >= existing
        // 8-9: For WHEN NOT MATCHED INSERT (CREATE_TIME, UPDATE_TIME)
        // Note: REF_ID check prevents stale updates from out-of-order events
        // Oracle requires CASE in SET clause for conditional updates based on src values
        String sql = "MERGE INTO RUNNING_TOTAL rt " +
                "USING (SELECT ? as PTS, ? as PROCESSING_ENTITY, ? as COUNTERPARTY_ID, ? as VALUE_DATE, ? as RUNNING_TOTAL, ? as REF_ID FROM DUAL) src " +
                "ON (rt.PTS = src.PTS AND rt.PROCESSING_ENTITY = src.PROCESSING_ENTITY AND rt.COUNTERPARTY_ID = src.COUNTERPARTY_ID AND rt.VALUE_DATE = src.VALUE_DATE) " +
                "WHEN MATCHED THEN UPDATE SET " +
                "rt.RUNNING_TOTAL = CASE WHEN src.REF_ID >= rt.REF_ID THEN src.RUNNING_TOTAL ELSE rt.RUNNING_TOTAL END, " +
                "rt.REF_ID = CASE WHEN src.REF_ID >= rt.REF_ID THEN src.REF_ID ELSE rt.REF_ID END, " +
                "rt.UPDATE_TIME = CASE WHEN src.REF_ID >= rt.REF_ID THEN ? ELSE rt.UPDATE_TIME END " +
                "WHEN NOT MATCHED THEN INSERT (PTS, PROCESSING_ENTITY, COUNTERPARTY_ID, VALUE_DATE, RUNNING_TOTAL, REF_ID, CREATE_TIME, UPDATE_TIME) " +
                "VALUES (src.PTS, src.PROCESSING_ENTITY, src.COUNTERPARTY_ID, src.VALUE_DATE, src.RUNNING_TOTAL, src.REF_ID, ?, ?)";

        Tuple params = Tuple.of(
                pts,                    // 1
                processingEntity,       // 2
                counterpartyId,         // 3
                valueDate,              // 4
                runningTotal,           // 5
                refId,                  // 6
                now,                    // 7: UPDATE_TIME for MATCHED
                now,                    // 8: CREATE_TIME for NOT MATCHED
                now                     // 9: UPDATE_TIME for NOT MATCHED
        );

        log.debug("SQL: {}", sql);
        log.debug("params: {}", params);

        return connection.preparedQuery(sql)
                .execute(params)
                .onSuccess(result -> log.debug("updateRunningTotal succeeded"))
                .onFailure(error -> log.error("updateRunningTotal failed: {}", error.getMessage(), error))
                .mapEmpty();
    }

    @Override
    public Future<java.util.Optional<RunningTotal>> getRunningTotal(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate
    ) {
        String sql = "SELECT RUNNING_TOTAL, REF_ID FROM RUNNING_TOTAL " +
                "WHERE PTS = ? AND PROCESSING_ENTITY = ? AND COUNTERPARTY_ID = ? AND VALUE_DATE = ?";

        Tuple params = Tuple.of(pts, processingEntity, counterpartyId, valueDate);

        return sqlClient.preparedQuery(sql)
                .execute(params)
                .map(result -> {
                    if (result.size() > 0) {
                        var row = result.iterator().next();
                        RunningTotalRepository.RunningTotal runningTotal = new RunningTotalRepository.RunningTotal(
                                row.getBigDecimal("RUNNING_TOTAL"), row.getLong("REF_ID")
                        );
                        return java.util.Optional.of(runningTotal);
                    }
                    return java.util.Optional.empty();
                });
    }

    @Override
    public Future<Void> calculateAndSaveRunningTotal(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate,
            Long maxSeqId,
            SqlConnection connection
    ) {
        // Log the parameters for debugging
        log.debug("calculateAndSaveRunningTotal: pts={}, pe={}, cp={}, vd={}, maxSeqId={}",
                pts, processingEntity, counterpartyId, valueDate, maxSeqId);

        // Combined SQL: Calculates running total from settlements and updates RUNNING_TOTAL in one operation
        // Uses MERGE for Oracle (UPSERT pattern)
        // Key optimization: Single round-trip, database-side calculation, atomic operation
        String sql = "MERGE INTO RUNNING_TOTAL rt " +
                "USING ( " +
                "  SELECT " +
                "    ? as PTS, " +
                "    ? as PROCESSING_ENTITY, " +
                "    ? as COUNTERPARTY_ID, " +
                "    ? as VALUE_DATE, " +
                "    COALESCE(SUM(s.AMOUNT * COALESCE(r.RATE_TO_USD, 1.0)), 0) as RUNNING_TOTAL, " +
                "    ? as REF_ID " +
                "  FROM SETTLEMENT s " +
                "  LEFT JOIN EXCHANGE_RATE r ON s.CURRENCY = r.CURRENCY " +
                "  WHERE s.PTS = ? " +
                "    AND s.PROCESSING_ENTITY = ? " +
                "    AND s.COUNTERPARTY_ID = ? " +
                "    AND s.VALUE_DATE = ? " +
                "    AND s.ID <= ? " +
                "    AND s.DIRECTION = 'PAY' " +
                "    AND s.BUSINESS_STATUS != 'CANCELLED' " +
                "    AND s.SETTLEMENT_VERSION = ( " +
                "      SELECT MAX(SETTLEMENT_VERSION) " +
                "      FROM SETTLEMENT " +
                "      WHERE SETTLEMENT_ID = s.SETTLEMENT_ID " +
                "        AND PTS = s.PTS " +
                "        AND PROCESSING_ENTITY = s.PROCESSING_ENTITY " +
                "    ) " +
                ") src " +
                "ON (rt.PTS = src.PTS " +
                "    AND rt.PROCESSING_ENTITY = src.PROCESSING_ENTITY " +
                "    AND rt.COUNTERPARTY_ID = src.COUNTERPARTY_ID " +
                "    AND rt.VALUE_DATE = src.VALUE_DATE) " +
                "WHEN MATCHED THEN UPDATE SET " +
                "  rt.RUNNING_TOTAL = CASE WHEN src.REF_ID >= rt.REF_ID THEN src.RUNNING_TOTAL ELSE rt.RUNNING_TOTAL END, " +
                "  rt.REF_ID = CASE WHEN src.REF_ID >= rt.REF_ID THEN src.REF_ID ELSE rt.REF_ID END, " +
                "  rt.UPDATE_TIME = CASE WHEN src.REF_ID >= rt.REF_ID THEN CURRENT_TIMESTAMP ELSE rt.UPDATE_TIME END " +
                "WHEN NOT MATCHED THEN INSERT " +
                "  (PTS, PROCESSING_ENTITY, COUNTERPARTY_ID, VALUE_DATE, RUNNING_TOTAL, REF_ID, CREATE_TIME, UPDATE_TIME) " +
                "VALUES (src.PTS, src.PROCESSING_ENTITY, src.COUNTERPARTY_ID, src.VALUE_DATE, src.RUNNING_TOTAL, src.REF_ID, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

        Tuple params = Tuple.of(
                pts,                    // 1: For USING SELECT (PTS)
                processingEntity,       // 2: For USING SELECT (PROCESSING_ENTITY)
                counterpartyId,         // 3: For USING SELECT (COUNTERPARTY_ID)
                valueDate,              // 4: For USING SELECT (VALUE_DATE)
                maxSeqId,               // 5: For USING SELECT (REF_ID)
                pts,                    // 6: For WHERE clause (PTS)
                processingEntity,       // 7: For WHERE clause (PROCESSING_ENTITY)
                counterpartyId,         // 8: For WHERE clause (COUNTERPARTY_ID)
                valueDate,              // 9: For WHERE clause (VALUE_DATE)
                maxSeqId                // 10: For WHERE clause (ID <= maxSeqId)
        );

        log.debug("SQL: {}", sql);
        log.debug("params: {}", params);

        return connection.preparedQuery(sql)
                .execute(params)
                .onSuccess(result -> log.debug("calculateAndSaveRunningTotal succeeded"))
                .onFailure(error -> log.error("calculateAndSaveRunningTotal failed: {}", error.getMessage(), error))
                .mapEmpty();
    }
}
