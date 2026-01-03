package com.tvpc.repository.impl;

import com.tvpc.repository.ExchangeRateRepository;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * JDBC implementation of ExchangeRateRepository
 */
public class JdbcExchangeRateRepository implements ExchangeRateRepository {

    private final SqlClient sqlClient;

    public JdbcExchangeRateRepository(SqlClient sqlClient) {
        this.sqlClient = sqlClient;
    }

    @Override
    public Future<java.util.Optional<BigDecimal>> getRate(String currency) {
        String sql = "SELECT RATE_TO_USD FROM EXCHANGE_RATE WHERE CURRENCY = ?";

        return sqlClient.preparedQuery(sql)
                .execute(Tuple.of(currency))
                .map(result -> {
                    if (result.size() > 0) {
                        return java.util.Optional.of(result.iterator().next().getBigDecimal("RATE_TO_USD"));
                    }
                    return java.util.Optional.empty();
                });
    }

    @Override
    public Future<Map<String, BigDecimal>> getAllRates() {
        String sql = "SELECT CURRENCY, RATE_TO_USD FROM EXCHANGE_RATE";

        return sqlClient.query(sql)
                .execute()
                .map(result -> {
                    Map<String, BigDecimal> rates = new HashMap<>();
                    for (var row : result) {
                        rates.put(row.getString("CURRENCY"), row.getBigDecimal("RATE_TO_USD"));
                    }
                    return rates;
                });
    }

    @Override
    public Future<Void> saveRate(String currency, BigDecimal rateToUSD) {
        String sql = "MERGE INTO EXCHANGE_RATE er " +
                "USING (SELECT ? as CURRENCY, ? as RATE_TO_USD FROM DUAL) src " +
                "ON (er.CURRENCY = src.CURRENCY) " +
                "WHEN MATCHED THEN UPDATE SET er.RATE_TO_USD = src.RATE_TO_USD, er.UPDATE_TIME = ? " +
                "WHEN NOT MATCHED THEN INSERT (CURRENCY, RATE_TO_USD, UPDATE_TIME) " +
                "VALUES (src.CURRENCY, src.RATE_TO_USD, ?)";

        Tuple params = Tuple.of(currency, rateToUSD, LocalDateTime.now(), LocalDateTime.now());

        return sqlClient.preparedQuery(sql)
                .execute(params)
                .mapEmpty();
    }

    @Override
    public Future<Boolean> areRatesStale() {
        String sql = "SELECT COUNT(*) as count FROM EXCHANGE_RATE WHERE UPDATE_TIME < (CURRENT_TIMESTAMP - INTERVAL '24' HOUR)";

        return sqlClient.query(sql)
                .execute()
                .map(result -> {
                    if (result.size() > 0) {
                        long count = result.iterator().next().getLong("count");
                        return count > 0;
                    }
                    return false;
                });
    }
}
