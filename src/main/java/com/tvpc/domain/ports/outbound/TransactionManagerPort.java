package com.tvpc.domain.ports.outbound;

import io.vertx.core.Future;
import io.vertx.sqlclient.SqlConnection;

import java.util.function.Function;

/**
 * Outbound port for transaction management.
 * Encapsulates all database transaction logic.
 */
public interface TransactionManagerPort {

    /**
     * Executes an operation within a database transaction.
     * Handles connection management, commit, and rollback automatically.
     *
     * @param operation The operation to execute with a connection
     * @param <T> The return type
     * @return Future with the result
     */
    <T> Future<T> executeInTransaction(Function<SqlConnection, Future<T>> operation);
}
