package com.tvpc.infrastructure.database;

import com.tvpc.domain.ports.outbound.ExchangeRateRepositoryPort;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.Row;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC implementation of ExchangeRateRepositoryPort.
 * Manages currency exchange rate storage and retrieval.
 */
public class JdbcExchangeRateRepository implements ExchangeRateRepositoryPort {

    private final JdbcTransactionManager transactionManager;

    public JdbcExchangeRateRepository(JdbcTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public Future<Optional<BigDecimal>> getRate(String currency) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Optional<BigDecimal>> promise = Promise.promise();

            String sql = "SELECT RATE_TO_USD FROM EXCHANGE_RATE WHERE CURRENCY = ?";

            connection.preparedQuery(sql)
                    .execute(Tuple.of(currency))
                    .onSuccess(result -> {
                        if (result.size() > 0) {
                            BigDecimal rate = result.iterator().next().getBigDecimal("RATE_TO_USD");
                            promise.complete(Optional.ofNullable(rate));
                        } else {
                            promise.complete(Optional.empty());
                        }
                    })
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    @Override
    public Future<Map<String, BigDecimal>> getAllRates() {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Map<String, BigDecimal>> promise = Promise.promise();

            String sql = "SELECT CURRENCY, RATE_TO_USD FROM EXCHANGE_RATE";

            connection.query(sql)
                    .execute()
                    .onSuccess(result -> {
                        Map<String, BigDecimal> rates = new HashMap<>();
                        for (Row row : result) {
                            rates.put(row.getString("CURRENCY"), row.getBigDecimal("RATE_TO_USD"));
                        }
                        promise.complete(rates);
                    })
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    @Override
    public Future<Void> saveRate(String currency, BigDecimal rateToUSD) {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Void> promise = Promise.promise();

            String sql = "MERGE INTO EXCHANGE_RATE er " +
                    "USING (SELECT ? as CURRENCY, ? as RATE_TO_USD FROM DUAL) src " +
                    "ON (er.CURRENCY = src.CURRENCY) " +
                    "WHEN MATCHED THEN UPDATE SET er.RATE_TO_USD = src.RATE_TO_USD, er.UPDATE_TIME = SYSTIMESTAMP " +
                    "WHEN NOT MATCHED THEN INSERT (CURRENCY, RATE_TO_USD) VALUES (src.CURRENCY, src.RATE_TO_USD)";

            connection.preparedQuery(sql)
                    .execute(Tuple.of(currency, rateToUSD))
                    .onSuccess(result -> promise.complete())
                    .onFailure(promise::fail);

            return promise.future();
        });
    }

    @Override
    public Future<Boolean> areRatesStale() {
        return transactionManager.executeInTransaction(connection -> {
            Promise<Boolean> promise = Promise.promise();

            // Check if any rates are older than 5 minutes (configurable)
            // For MVP, we'll return false (not stale) since rates are manually updated
            String sql = "SELECT COUNT(*) as count FROM EXCHANGE_RATE " +
                    "WHERE UPDATE_TIME < (SYSTIMESTAMP - INTERVAL '5' MINUTE)";

            connection.preparedQuery(sql)
                    .execute()
                    .onSuccess(result -> {
                        long count = result.iterator().next().getLong("count");
                        // If any rates are stale, return true
                        promise.complete(count > 0);
                    })
                    .onFailure(promise::fail);

            return promise.future();
        });
    }
}
