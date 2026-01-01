package com.tvpc.infrastructure.database;

import com.tvpc.domain.Settlement;
import com.tvpc.domain.ports.outbound.SettlementRepositoryPort;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of SettlementRepositoryPort.
 * Manages database operations for settlements using Vert.x SQL client.
 */
public class JdbcSettlementRepository implements SettlementRepositoryPort {

    private final JdbcTransactionManager transactionManager;

    public JdbcSettlementRepository(JdbcTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Future<Long> save(Settlement settlement) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Long> promise = Promise.promise();

            // First, get the next sequence value
            connection.query("SELECT SETTLEMENT_SEQ.NEXTVAL AS ID FROM DUAL").execute()
                    .compose(result -> {
                        if (result.size() == 0) {
                            promise.fail("No sequence value returned");
                            return Future.failedFuture("No sequence value");
                        }

                        Long id = result.iterator().next().getLong("ID");

                        // Now insert with the explicit ID
                        String sql = "INSERT INTO SETTLEMENT (" +
                                "ID, SETTLEMENT_ID, SETTLEMENT_VERSION, PTS, PROCESSING_ENTITY, " +
                                "COUNTERPARTY_ID, VALUE_DATE, CURRENCY, AMOUNT, " +
                                "BUSINESS_STATUS, DIRECTION, GROSS_NET, IS_OLD, " +
                                "CREATE_TIME, UPDATE_TIME" +
                                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

                        return connection.preparedQuery(sql)
                                .execute(Tuple.of(
                                        id,
                                        settlement.getSettlementId(),
                                        settlement.getSettlementVersion(),
                                        settlement.getPts(),
                                        settlement.getProcessingEntity(),
                                        settlement.getCounterpartyId(),
                                        settlement.getValueDate(),
                                        settlement.getCurrency(),
                                        settlement.getAmount(),
                                        settlement.getBusinessStatus().name(),
                                        settlement.getDirection().name(),
                                        settlement.getSettlementType().name(),
                                        0  // IS_OLD = 0 (false)
                                ))
                                .map(id);
                    })
                    .onSuccess(promise::complete)
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    @Override
    public Future<Void> markOldVersions(String settlementId, String pts, String processingEntity) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Void> promise = Promise.promise();

            String sql = "UPDATE SETTLEMENT SET IS_OLD = 1 " +
                    "WHERE SETTLEMENT_ID = ? AND PTS = ? AND PROCESSING_ENTITY = ? " +
                    "AND IS_OLD = 0";

            connection.preparedQuery(sql)
                    .execute(Tuple.of(settlementId, pts, processingEntity))
                    .onSuccess(result -> promise.complete())
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    @Override
    public Future<Optional<String>> findPreviousCounterparty(
            String settlementId, String pts, String processingEntity, Long currentId) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Optional<String>> promise = Promise.promise();

            String sql = "SELECT COUNTERPARTY_ID FROM SETTLEMENT " +
                    "WHERE ID = (SELECT MAX(ID) FROM SETTLEMENT " +
                    "            WHERE SETTLEMENT_ID = ? AND PTS = ? AND PROCESSING_ENTITY = ? AND ID < ?)";

            connection.preparedQuery(sql)
                    .execute(Tuple.of(settlementId, pts, processingEntity, currentId))
                    .onSuccess(result -> {
                        if (result.size() > 0) {
                            String counterparty = result.iterator().next().getString("COUNTERPARTY_ID");
                            promise.complete(Optional.ofNullable(counterparty));
                        } else {
                            promise.complete(Optional.empty());
                        }
                    })
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    @Override
    public Future<Optional<Settlement>> findLatestVersion(String settlementId, String pts, String processingEntity) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Optional<Settlement>> promise = Promise.promise();

            String sql = "SELECT * FROM SETTLEMENT " +
                    "WHERE SETTLEMENT_ID = ? AND PTS = ? AND PROCESSING_ENTITY = ? " +
                    "ORDER BY SETTLEMENT_VERSION DESC, ID DESC " +
                    "FETCH FIRST 1 ROWS ONLY";

            connection.preparedQuery(sql)
                    .execute(Tuple.of(settlementId, pts, processingEntity))
                    .onSuccess(result -> {
                        if (result.size() > 0) {
                            promise.complete(Optional.of(mapToSettlement(result.iterator().next())));
                        } else {
                            promise.complete(Optional.empty());
                        }
                    })
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    @Override
    public Future<List<Settlement>> findByGroupWithFilters(
            String pts, String processingEntity, String counterpartyId, String valueDate, Long maxSeqId) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<List<Settlement>> promise = Promise.promise();

            String sql = "SELECT s.* FROM SETTLEMENT s " +
                    "INNER JOIN (" +
                    "  SELECT SETTLEMENT_ID, MAX(SETTLEMENT_VERSION) as max_version " +
                    "  FROM SETTLEMENT " +
                    "  WHERE PTS = ? AND PROCESSING_ENTITY = ? AND COUNTERPARTY_ID = ? AND VALUE_DATE = ? " +
                    "    AND ID <= ? " +
                    "  GROUP BY SETTLEMENT_ID" +
                    ") latest ON s.SETTLEMENT_ID = latest.SETTLEMENT_ID AND s.SETTLEMENT_VERSION = latest.max_version " +
                    "WHERE s.DIRECTION = 'PAY' AND s.BUSINESS_STATUS != 'CANCELLED' " +
                    "ORDER BY s.ID";

            connection.preparedQuery(sql)
                    .execute(Tuple.of(pts, processingEntity, counterpartyId, LocalDate.parse(valueDate), maxSeqId))
                    .onSuccess(result -> {
                        List<Settlement> settlements = new ArrayList<>();
                        for (Row row : result) {
                            settlements.add(mapToSettlement(row));
                        }
                        promise.complete(settlements);
                    })
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    private Settlement mapToSettlement(Row row) {
        Settlement settlement = new Settlement(
                row.getString("SETTLEMENT_ID"),
                row.getLong("SETTLEMENT_VERSION"),
                row.getString("PTS"),
                row.getString("PROCESSING_ENTITY"),
                row.getString("COUNTERPARTY_ID"),
                row.getLocalDate("VALUE_DATE"),
                row.getString("CURRENCY"),
                row.getBigDecimal("AMOUNT"),
                com.tvpc.domain.BusinessStatus.valueOf(row.getString("BUSINESS_STATUS")),
                com.tvpc.domain.SettlementDirection.valueOf(row.getString("DIRECTION")),
                com.tvpc.domain.SettlementType.valueOf(row.getString("GROSS_NET"))
        );
        settlement.setId(row.getLong("ID"));
        // Oracle returns NUMBER(1) as Integer, need to convert to boolean
        Object isOldValue = row.getValue("IS_OLD");
        boolean isOld = false;
        if (isOldValue instanceof Number) {
            isOld = ((Number) isOldValue).intValue() == 1;
        } else if (isOldValue instanceof Boolean) {
            isOld = (Boolean) isOldValue;
        }
        settlement.setIsOld(isOld);
        return settlement;
    }
}
