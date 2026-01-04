package com.tvpc.infrastructure.persistence;

import com.tvpc.application.port.outbound.ExchangeRateRepository;
import com.tvpc.domain.model.ExchangeRate;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlClient;
import io.vertx.sqlclient.Tuple;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * JDBC implementation of ExchangeRateRepository
 * Infrastructure layer adapter
 */
@Slf4j
public class JdbcExchangeRateRepository implements ExchangeRateRepository {

    private final SqlClient sqlClient;

    public JdbcExchangeRateRepository(SqlClient sqlClient) {
        this.sqlClient = sqlClient;
    }

    @Override
    public Future<Optional<ExchangeRate>> getRate(String currency) {
        String sql = "SELECT * FROM EXCHANGE_RATE WHERE CURRENCY = ?";

        return sqlClient.preparedQuery(sql)
                .execute(Tuple.of(currency))
                .map(result -> {
                    if (result.size() > 0) {
                        var row = result.iterator().next();
                        return Optional.of(new ExchangeRate(
                                row.getString("CURRENCY"),
                                row.getBigDecimal("RATE_TO_USD"),
                                row.getLocalDateTime("UPDATE_TIME")
                        ));
                    }
                    return Optional.empty();
                });
    }

    @Override
    public Future<BigDecimal> convertToUsd(BigDecimal amount, String currency) {
        if ("USD".equals(currency)) {
            return Future.succeededFuture(amount);
        }

        return getRate(currency)
                .map(optional -> {
                    if (optional.isEmpty()) {
                        throw new RuntimeException("No exchange rate found for currency: " + currency);
                    }
                    return amount.multiply(optional.get().getRateToUsd());
                });
    }

    @Override
    public Future<Void> saveRate(String currency, BigDecimal rateToUsd) {
        String sql = "MERGE INTO EXCHANGE_RATE ER " +
                "USING (SELECT ? AS CURRENCY, ? AS RATE_TO_USD FROM DUAL) SRC " +
                "ON (ER.CURRENCY = SRC.CURRENCY) " +
                "WHEN MATCHED THEN UPDATE SET ER.RATE_TO_USD = SRC.RATE_TO_USD, ER.UPDATE_TIME = ? " +
                "WHEN NOT MATCHED THEN INSERT (CURRENCY, RATE_TO_USD, UPDATE_TIME) " +
                "VALUES (SRC.CURRENCY, SRC.RATE_TO_USD, ?)";

        LocalDateTime now = LocalDateTime.now();
        Tuple params = Tuple.of(currency, rateToUsd, now, now);

        return sqlClient.preparedQuery(sql)
                .execute(params)
                .mapEmpty();
    }
}
