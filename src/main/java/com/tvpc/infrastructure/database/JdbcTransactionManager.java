package com.tvpc.infrastructure.database;

import com.tvpc.domain.ports.outbound.TransactionManagerPort;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Transaction;
import io.vertx.jdbcclient.JDBCPool;

import java.util.function.Function;

/**
 * JDBC implementation of TransactionManagerPort.
 * Manages database transactions for all repository operations.
 * This is the central point for transaction management in the application.
 */
public class JdbcTransactionManager implements TransactionManagerPort {

    private final JDBCPool pool;

    public JdbcTransactionManager(JDBCPool pool) {
        this.pool = pool;
    }

    @Override
    public <T> Future<T> executeInTransaction(Function<SqlConnection, Future<T>> operation) {
        Promise<T> promise = Promise.promise();

        pool.getConnection()
                .compose(connection -> {
                    // Begin transaction
                    return connection.begin()
                            .compose(transaction -> {
                                // Execute the operation
                                return operation.apply(connection)
                                        .compose(result -> {
                                            // Commit on success
                                            return transaction.commit()
                                                    .map(result)
                                                    .onSuccess(v -> {
                                                        connection.close();
                                                        promise.complete(result);
                                                    })
                                                    .onFailure(err -> {
                                                        connection.close();
                                                        promise.fail(err);
                                                    });
                                        })
                                        .onFailure(err -> {
                                            // Rollback on failure
                                            transaction.rollback()
                                                    .onComplete(v -> connection.close());
                                            promise.fail(err);
                                        });
                            });
                })
                .onFailure(promise::fail);

        return promise.future();
    }

    /**
     * Get the underlying JDBC pool
     */
    public JDBCPool getPool() {
        return pool;
    }
}
