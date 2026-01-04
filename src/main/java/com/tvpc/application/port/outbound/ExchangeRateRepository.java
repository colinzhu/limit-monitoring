package com.tvpc.application.port.outbound;

import com.tvpc.domain.model.ExchangeRate;
import io.vertx.core.Future;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Outbound port - Repository for Exchange Rate
 * Secondary port (driven by the infrastructure layer)
 */
public interface ExchangeRateRepository {
    /**
     * Get exchange rate for a currency
     * @param currency ISO 4217 currency code
     * @return Future with optional exchange rate
     */
    Future<Optional<ExchangeRate>> getRate(String currency);

    /**
     * Convert amount to USD
     * @param amount Amount in original currency
     * @param currency Currency code
     * @return Future with USD equivalent
     */
    Future<BigDecimal> convertToUsd(BigDecimal amount, String currency);

    /**
     * Save or update exchange rate
     * @param currency Currency code
     * @param rateToUsd Rate to USD
     * @return Future indicating completion
     */
    Future<Void> saveRate(String currency, BigDecimal rateToUsd);
}
