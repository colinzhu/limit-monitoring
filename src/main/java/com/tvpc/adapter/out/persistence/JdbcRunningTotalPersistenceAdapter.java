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
            SqlConnection connection
    ) {
        log.debug("calculateAndSaveRunningTotal: pts={}, pe={}, cp={}, vd={}, maxSeqId={}",
                pts, processingEntity, counterpartyId, valueDate, maxSeqId);

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

        return connection.preparedQuery(sql)
                .execute(params)
                .onSuccess(result -> log.debug("calculateAndSaveRunningTotal succeeded"))
                .onFailure(error -> log.error("calculateAndSaveRunningTotal failed: {}", error.getMessage(), error))
                .mapEmpty();
    }
}
