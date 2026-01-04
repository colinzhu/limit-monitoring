package com.tvpc.domain.port.repository;

import io.vertx.core.Future;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Repository port for Exchange Rate operations
 * This is a domain port (interface) that defines the contract for exchange rate persistence.
 * Infrastructure layer will provide the implementation.
 */
public interface ExchangeRateRepository {

    /**
     * Get exchange rate for a currency
     * @param currency ISO 4217 currency code
     * @return Future with optional exchange rate
     */
    Future<java.util.Optional<BigDecimal>> getRate(String currency);

    /**
     * Get all exchange rates
     * @return Future with map of currency to rate
     */
    Future<Map<String, BigDecimal>> getAllRates();

    /**
     * Save or update exchange rate
     * @param currency ISO 4217 currency code
     * @param rateToUSD Conversion rate to USD
     * @return Future indicating completion
     */
    Future<Void> saveRate(String currency, BigDecimal rateToUSD);
}

