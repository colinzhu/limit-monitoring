package com.tvpc.adapter.out.persistence;

import com.tvpc.application.port.out.RunningTotalRepository;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * JDBC implementation of RunningTotalRepository
 */
@Slf4j
@RequiredArgsConstructor
public class JdbcRunningTotalPersistenceAdapter implements RunningTotalRepository {

    private final SqlClient sqlClient;

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

        log.debug("updateRunningTotal: pts={}, pe={}, cp={}, vd={}, total={}, refId={}",
                pts, processingEntity, counterpartyId, valueDate, runningTotal, refId);

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
                pts,
                processingEntity,
                counterpartyId,
                valueDate,
                runningTotal,
                refId,
                now,
                now,
                now
        );

        return connection.preparedQuery(sql)
                .execute(params)
                .onSuccess(result -> log.debug("updateRunningTotal succeeded"))
                .onFailure(error -> log.error("updateRunningTotal failed: {}", error.getMessage(), error))
                .mapEmpty();
    }

    @Override
    public Future<Optional<RunningTotalData>> getRunningTotal(
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
                        return Optional.of(new RunningTotalData(
                                row.getBigDecimal("RUNNING_TOTAL"),
                                row.getLong("REF_ID")
                        ));
                    }
                    return Optional.empty();
                });
    }

    @Override
    public Future<Void> calculateAndSaveRunningTotal(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate,
            Long maxSeqId,
            SqlConnection connection,
            java.util.Set<String> includedBusinessStatuses,
            java.util.Set<String> includedDirections,
            java.util.Set<String> includedSettlementTypes
    ) {
        log.debug("calculateAndSaveRunningTotal: pts={}, pe={}, cp={}, vd={}, maxSeqId={}, rules=[statuses={}, directions={}, types={}]",
                pts, processingEntity, counterpartyId, valueDate, maxSeqId, 
                includedBusinessStatuses, includedDirections, includedSettlementTypes);

        // Build dynamic WHERE clause based on rules
        StringBuilder whereClause = new StringBuilder();
        whereClause.append("WHERE s.PTS = ? ")
                .append("AND s.PROCESSING_ENTITY = ? ")
                .append("AND s.COUNTERPARTY_ID = ? ")
                .append("AND s.VALUE_DATE = ? ")
                .append("AND s.ID <= ? ");

        // Add business status filter
        if (!includedBusinessStatuses.isEmpty()) {
            whereClause.append("AND s.BUSINESS_STATUS IN (");
            whereClause.append(String.join(",", includedBusinessStatuses.stream()
                    .map(s -> "'" + s + "'")
                    .toList()));
            whereClause.append(") ");
        }

        // Add direction filter
        if (!includedDirections.isEmpty()) {
            whereClause.append("AND s.DIRECTION IN (");
            whereClause.append(String.join(",", includedDirections.stream()
                    .map(s -> "'" + s + "'")
                    .toList()));
            whereClause.append(") ");
        }

        // Add settlement type filter
        if (!includedSettlementTypes.isEmpty()) {
            whereClause.append("AND s.SETTLEMENT_TYPE IN (");
            whereClause.append(String.join(",", includedSettlementTypes.stream()
                    .map(s -> "'" + s + "'")
                    .toList()));
            whereClause.append(") ");
        }

        whereClause.append("AND s.SETTLEMENT_VERSION = ( ")
                .append("  SELECT MAX(SETTLEMENT_VERSION) ")
                .append("  FROM SETTLEMENT ")
                .append("  WHERE SETTLEMENT_ID = s.SETTLEMENT_ID ")
                .append("    AND PTS = s.PTS ")
                .append("    AND PROCESSING_ENTITY = s.PROCESSING_ENTITY ")
                .append(") ");

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
                "  " + whereClause.toString() +
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
                pts,
                processingEntity,
                counterpartyId,
                valueDate,
                maxSeqId,
                pts,
                processingEntity,
                counterpartyId,
                valueDate,
                maxSeqId
        );

        log.debug("Executing SQL with dynamic rules: {}", sql);

        return connection.preparedQuery(sql)
                .execute(params)
                .onSuccess(result -> log.debug("calculateAndSaveRunningTotal succeeded"))
                .onFailure(error -> log.error("calculateAndSaveRunningTotal failed: {}", error.getMessage(), error))
                .mapEmpty();
    }
}
