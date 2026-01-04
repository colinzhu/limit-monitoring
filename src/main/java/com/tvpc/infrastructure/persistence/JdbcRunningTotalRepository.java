package com.tvpc.infrastructure.persistence;

import com.tvpc.application.port.outbound.RunningTotalRepository;
import com.tvpc.domain.model.RunningTotal;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * JDBC implementation of RunningTotalRepository
 * Infrastructure layer adapter
 */
@Slf4j
public class JdbcRunningTotalRepository implements RunningTotalRepository {

    private final io.vertx.sqlclient.SqlClient sqlClient;

    public JdbcRunningTotalRepository(io.vertx.sqlclient.SqlClient sqlClient) {
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
        String sql = "MERGE INTO RUNNING_TOTAL RT " +
                "USING (SELECT ? AS PTS, ? AS PROCESSING_ENTITY, ? AS COUNTERPARTY_ID, ? AS VALUE_DATE, ? AS RUNNING_TOTAL, ? AS REF_ID FROM DUAL) SRC " +
                "ON (RT.PTS = SRC.PTS AND RT.PROCESSING_ENTITY = SRC.PROCESSING_ENTITY AND RT.COUNTERPARTY_ID = SRC.COUNTERPARTY_ID AND RT.VALUE_DATE = SRC.VALUE_DATE) " +
                "WHEN MATCHED THEN UPDATE SET RT.RUNNING_TOTAL = SRC.RUNNING_TOTAL, RT.REF_ID = SRC.REF_ID, RT.UPDATE_TIME = ? " +
                "WHEN NOT MATCHED THEN INSERT (PTS, PROCESSING_ENTITY, COUNTERPARTY_ID, VALUE_DATE, RUNNING_TOTAL, REF_ID, CREATE_TIME, UPDATE_TIME) " +
                "VALUES (SRC.PTS, SRC.PROCESSING_ENTITY, SRC.COUNTERPARTY_ID, SRC.VALUE_DATE, SRC.RUNNING_TOTAL, SRC.REF_ID, ?, ?)";

        Tuple params = Tuple.of(
                pts, processingEntity, counterpartyId, valueDate, runningTotal, refId,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()
        );

        return connection.preparedQuery(sql)
                .execute(params)
                .mapEmpty();
    }

    @Override
    public Future<Optional<RunningTotal>> getRunningTotal(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDate
    ) {
        String sql = "SELECT * FROM RUNNING_TOTAL " +
                "WHERE PTS = ? AND PROCESSING_ENTITY = ? AND COUNTERPARTY_ID = ? AND VALUE_DATE = ?";

        Tuple params = Tuple.of(pts, processingEntity, counterpartyId, valueDate);

        return sqlClient.preparedQuery(sql)
                .execute(params)
                .map(result -> {
                    if (result.size() > 0) {
                        var row = result.iterator().next();
                        return Optional.of(new RunningTotal(
                                row.getLong("ID"),
                                row.getString("PTS"),
                                row.getString("PROCESSING_ENTITY"),
                                row.getString("COUNTERPARTY_ID"),
                                row.getLocalDate("VALUE_DATE").toString(),
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
        // OPTIMIZED: Single SQL that calculates sum and updates running total
        // This query:
        // 1. Filters settlements by group, direction, business status, and version
        // 2. Joins with EXCHANGE_RATE to convert to USD
        // 3. Sums the amounts
        // 4. Updates or inserts into RUNNING_TOTAL

        String sql = "MERGE INTO RUNNING_TOTAL RT " +
                "USING (" +
                "  SELECT " +
                "    ? AS PTS, " +
                "    ? AS PROCESSING_ENTITY, " +
                "    ? AS COUNTERPARTY_ID, " +
                "    ? AS VALUE_DATE, " +
                "    NVL(SUM(s.AMOUNT * er.RATE_TO_USD), 0) AS TOTAL_USD, " +
                "    ? AS REF_ID " +
                "  FROM SETTLEMENT s " +
                "  LEFT JOIN EXCHANGE_RATE er ON s.CURRENCY = er.CURRENCY " +
                "  WHERE s.PTS = ? " +
                "    AND s.PROCESSING_ENTITY = ? " +
                "    AND s.COUNTERPARTY_ID = ? " +
                "    AND s.VALUE_DATE = ? " +
                "    AND s.ID <= ? " +
                "    AND s.DIRECTION = 'PAY' " +
                "    AND s.BUSINESS_STATUS != 'CANCELLED' " +
                "    AND s.SETTLEMENT_VERSION = (" +
                "      SELECT MAX(SETTLEMENT_VERSION) " +
                "      FROM SETTLEMENT " +
                "      WHERE SETTLEMENT_ID = s.SETTLEMENT_ID " +
                "        AND PTS = s.PTS " +
                "        AND PROCESSING_ENTITY = s.PROCESSING_ENTITY " +
                "    )" +
                ") SRC " +
                "ON (RT.PTS = SRC.PTS AND RT.PROCESSING_ENTITY = SRC.PROCESSING_ENTITY AND RT.COUNTERPARTY_ID = SRC.COUNTERPARTY_ID AND RT.VALUE_DATE = SRC.VALUE_DATE) " +
                "WHEN MATCHED THEN UPDATE SET " +
                "  RT.RUNNING_TOTAL = SRC.TOTAL_USD, " +
                "  RT.REF_ID = SRC.REF_ID, " +
                "  RT.UPDATE_TIME = ? " +
                "WHEN NOT MATCHED THEN INSERT " +
                "  (PTS, PROCESSING_ENTITY, COUNTERPARTY_ID, VALUE_DATE, RUNNING_TOTAL, REF_ID, CREATE_TIME, UPDATE_TIME) " +
                "  VALUES (SRC.PTS, SRC.PROCESSING_ENTITY, SRC.COUNTERPARTY_ID, SRC.VALUE_DATE, SRC.TOTAL_USD, SRC.REF_ID, ?, ?)";

        Tuple params = Tuple.of(
                pts, processingEntity, counterpartyId, valueDate, maxSeqId,
                pts, processingEntity, counterpartyId, valueDate, maxSeqId,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()
        );

        return connection.preparedQuery(sql)
                .execute(params)
                .onSuccess(result -> log.debug("Updated running total for group: {}|{}|{}|{}", pts, processingEntity, counterpartyId, valueDate))
                .onFailure(error -> log.error("Failed to update running total for group: {}|{}|{}|{}", pts, processingEntity, counterpartyId, valueDate, error))
                .mapEmpty();
    }
}
