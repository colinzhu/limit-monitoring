package com.tvpc.domain.ports.outbound;

import io.vertx.core.Future;

import java.math.BigDecimal;

/**
 * Outbound port for configuration management.
 */
public interface ConfigurationServicePort {

    /**
     * Gets the exposure limit for a counterparty.
     *
     * @param counterpartyId The counterparty identifier (or null for default)
     * @return The exposure limit in USD
     */
    Future<BigDecimal> getExposureLimit(String counterpartyId);

    /**
     * Checks if filtering rules should include a settlement.
     *
     * @param direction The settlement direction
     * @param businessStatus The business status
     * @return True if settlement should be included in calculations
     */
    Future<Boolean> shouldIncludeInCalculation(String direction, String businessStatus);

    /**
     * Updates filtering rules from external system.
     *
     * @return Void future
     */
    Future<Void> updateRules();

    /**
     * Updates exchange rates from external system.
     *
     * @return Void future
     */
    Future<Void> updateExchangeRates();
}
