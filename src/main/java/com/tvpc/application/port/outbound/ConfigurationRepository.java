package com.tvpc.application.port.outbound;

import io.vertx.core.Future;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Outbound port - Repository for configuration (limits, rules)
 * Secondary port (driven by the infrastructure layer)
 */
public interface ConfigurationRepository {
    /**
     * Get exposure limit for a counterparty
     * @param counterpartyId Counterparty identifier
     * @return Future with optional limit (in USD)
     */
    Future<Optional<BigDecimal>> getExposureLimit(String counterpartyId);

    /**
     * Get default exposure limit (MVP mode)
     * @return Future with default limit
     */
    Future<BigDecimal> getDefaultExposureLimit();

    /**
     * Check if a settlement should be included based on filtering rules
     * @param businessStatus Settlement business status
     * @param direction Settlement direction
     * @return Future with true if included
     */
    Future<Boolean> shouldIncludeInRunningTotal(String businessStatus, String direction);

    /**
     * Refresh configuration from external system
     * @return Future indicating completion
     */
    Future<Void> refreshConfiguration();
}
