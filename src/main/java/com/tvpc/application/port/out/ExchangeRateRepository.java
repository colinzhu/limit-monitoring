package com.tvpc.application.port.out;

import io.vertx.core.Future;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Output port for exchange rate persistence operations
 */
public interface ExchangeRateRepository {

    /**
     * Get exchange rate for a currency
     */
    Future<Optional<BigDecimal>> getRate(String currency);

    /**
     * Get all exchange rates
     */
    Future<Map<String, BigDecimal>> getAllRates();

    /**
     * Save or update exchange rate
     */
    Future<Void> saveRate(String currency, BigDecimal rateToUSD);

    /**
     * Check if rates are stale (older than 24 hours)
     */
    Future<Boolean> areRatesStale();
}
