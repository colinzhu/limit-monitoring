package com.tvpc.domain.ports.inbound;

import io.vertx.core.Future;

/**
 * Inbound port for configuration management use case.
 */
public interface ConfigurationUseCase {

    /**
     * Updates exchange rates from external system.
     *
     * @return Void future
     */
    Future<Void> updateExchangeRates();

    /**
     * Updates filtering rules from external system.
     *
     * @return Void future
     */
    Future<Void> updateRules();

    /**
     * Updates counterparty-specific limits from external system.
     *
     * @return Void future
     */
    Future<Void> updateLimits();
}
