package com.tvpc.domain.ports.outbound;

import io.vertx.core.Future;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Outbound port for exchange rate operations.
 */
public interface ExchangeRateRepositoryPort {

    /**
     * Gets the exchange rate for a currency.
     *
     * @param currency The currency code (3 characters)
     * @return Optional containing rate to USD, or empty if not found
     */
    Future<Optional<BigDecimal>> getRate(String currency);

    /**
     * Gets all current exchange rates.
     *
     * @return Map of currency to rate
     */
    Future<Map<String, BigDecimal>> getAllRates();

    /**
     * Saves or updates an exchange rate.
     *
     * @param currency The currency code
     * @param rateToUSD The rate to USD
     * @return Void future
     */
    Future<Void> saveRate(String currency, BigDecimal rateToUSD);

    /**
     * Checks if exchange rates are stale (need refresh).
     *
     * @return True if rates need updating
     */
    Future<Boolean> areRatesStale();
}
