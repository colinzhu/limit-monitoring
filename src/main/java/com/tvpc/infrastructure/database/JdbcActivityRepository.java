package com.tvpc.infrastructure.database;

import com.tvpc.domain.WorkflowInfo;
import com.tvpc.domain.ports.outbound.ActivityRepositoryPort;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of ActivityRepositoryPort.
 * Manages audit trail and activity logging.
 */
public class JdbcActivityRepository implements ActivityRepositoryPort {

    private final JdbcTransactionManager transactionManager;

    public JdbcActivityRepository(JdbcTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Future<Void> recordActivity(
            String pts, String processingEntity, String settlementId, Long settlementVersion,
            String userId, String userName, String actionType, String actionComment) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Void> promise = Promise.promise();

            String sql = "INSERT INTO ACTIVITIES " +
                    "(PTS, PROCESSING_ENTITY, SETTLEMENT_ID, SETTLEMENT_VERSION, USER_ID, USER_NAME, ACTION_TYPE, ACTION_COMMENT) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            connection.preparedQuery(sql)
                    .execute(Tuple.of(
                            pts, processingEntity, settlementId, settlementVersion,
                            userId, userName, actionType, actionComment
                    ))
                    .onSuccess(result -> promise.complete())
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    @Override
    public Future<Boolean> hasUserRequested(String settlementId, Long settlementVersion, String userId) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Boolean> promise = Promise.promise();

            String sql = "SELECT COUNT(*) as count FROM ACTIVITIES " +
                    "WHERE SETTLEMENT_ID = ? AND SETTLEMENT_VERSION = ? " +
                    "AND USER_ID = ? AND ACTION_TYPE = 'REQUEST'";

            connection.preparedQuery(sql)
                    .execute(Tuple.of(settlementId, settlementVersion, userId))
                    .onSuccess(result -> {
                        long count = result.iterator().next().getLong("count");
                        promise.complete(count > 0);
                    })
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    @Override
    public Future<Boolean> isAuthorized(String settlementId, Long settlementVersion) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Boolean> promise = Promise.promise();

            String sql = "SELECT COUNT(*) as count FROM ACTIVITIES " +
                    "WHERE SETTLEMENT_ID = ? AND SETTLEMENT_VERSION = ? " +
                    "AND ACTION_TYPE = 'AUTHORISE'";

            connection.preparedQuery(sql)
                    .execute(Tuple.of(settlementId, settlementVersion))
                    .onSuccess(result -> {
                        long count = result.iterator().next().getLong("count");
                        promise.complete(count > 0);
                    })
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    @Override
    public Future<WorkflowInfo> getWorkflowInfo(String settlementId, Long settlementVersion) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<WorkflowInfo> promise = Promise.promise();

            String sql = "SELECT USER_ID, ACTION_TYPE FROM ACTIVITIES " +
                    "WHERE SETTLEMENT_ID = ? AND SETTLEMENT_VERSION = ? " +
                    "AND ACTION_TYPE IN ('REQUEST', 'AUTHORISE')";

            connection.preparedQuery(sql)
                    .execute(Tuple.of(settlementId, settlementVersion))
                    .onSuccess(result -> {
                        List<String> requesters = new ArrayList<>();
                        List<String> authorizers = new ArrayList<>();
                        boolean hasUserRequested = false;
                        boolean isAuthorized = false;

                        for (Row row : result) {
                            String userId = row.getString("USER_ID");
                            String actionType = row.getString("ACTION_TYPE");

                            if ("REQUEST".equals(actionType)) {
                                requesters.add(userId);
                                hasUserRequested = true;
                            } else if ("AUTHORISE".equals(actionType)) {
                                authorizers.add(userId);
                                isAuthorized = true;
                            }
                        }

                        promise.complete(new WorkflowInfo(hasUserRequested, isAuthorized, requesters, authorizers));
                    })
                    .onFailure(promise::fail);

            return promise.future();
        });
    }
}
