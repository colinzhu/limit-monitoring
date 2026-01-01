package com.tvpc.infrastructure.database;

import com.tvpc.domain.RunningTotal;
import com.tvpc.domain.ports.outbound.RunningTotalRepositoryPort;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.Row;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * JDBC implementation of RunningTotalRepositoryPort.
 * Manages running total calculations and storage.
 */
public class JdbcRunningTotalRepository implements RunningTotalRepositoryPort {

    private final JdbcTransactionManager transactionManager;

    public JdbcRunningTotalRepository(JdbcTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Future<Void> updateRunningTotal(
            String pts, String processingEntity, String counterpartyId,
            LocalDate valueDate, BigDecimal runningTotal, Long refId) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Void> promise = Promise.promise();

            String sql = "MERGE INTO RUNNING_TOTAL rt " +
                    "USING (SELECT ? as PTS, ? as PROCESSING_ENTITY, ? as COUNTERPARTY_ID, " +
                    "              ? as VALUE_DATE, ? as RUNNING_TOTAL, ? as REF_ID FROM DUAL) src " +
                    "ON (rt.PTS = src.PTS AND rt.PROCESSING_ENTITY = src.PROCESSING_ENTITY " +
                    "    AND rt.COUNTERPARTY_ID = src.COUNTERPARTY_ID AND rt.VALUE_DATE = src.VALUE_DATE) " +
                    "WHEN MATCHED THEN UPDATE SET rt.RUNNING_TOTAL = src.RUNNING_TOTAL, rt.REF_ID = src.REF_ID, rt.UPDATE_TIME = SYSTIMESTAMP " +
                    "WHEN NOT MATCHED THEN INSERT (PTS, PROCESSING_ENTITY, COUNTERPARTY_ID, VALUE_DATE, RUNNING_TOTAL, REF_ID) " +
                    "VALUES (src.PTS, src.PROCESSING_ENTITY, src.COUNTERPARTY_ID, src.VALUE_DATE, src.RUNNING_TOTAL, src.REF_ID)";

            connection.preparedQuery(sql)
                    .execute(Tuple.of(pts, processingEntity, counterpartyId, valueDate, runningTotal, refId))
                    .onSuccess(result -> promise.complete())
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    @Override
    public Future<Optional<RunningTotal>> getRunningTotal(
            String pts, String processingEntity, String counterpartyId, LocalDate valueDate) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Optional<RunningTotal>> promise = Promise.promise();

            String sql = "SELECT * FROM RUNNING_TOTAL " +
                    "WHERE PTS = ? AND PROCESSING_ENTITY = ? AND COUNTERPARTY_ID = ? AND VALUE_DATE = ?";

            connection.preparedQuery(sql)
                    .execute(Tuple.of(pts, processingEntity, counterpartyId, valueDate))
                    .onSuccess(result -> {
                        if (result.size() > 0) {
                            Row row = result.iterator().next();
                            RunningTotal runningTotal = new RunningTotal(
                                    pts, processingEntity, counterpartyId, valueDate,
                                    row.getBigDecimal("RUNNING_TOTAL"), row.getLong("REF_ID")
                            );
                            promise.complete(Optional.of(runningTotal));
                        } else {
                            promise.complete(Optional.empty());
                        }
                    })
                    .onFailure(promise::fail);

            return promise.future();
        });
    }
}
