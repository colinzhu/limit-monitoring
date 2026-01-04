package com.tvpc.infrastructure.config;

import com.tvpc.application.port.outbound.ConfigurationRepository;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * In-memory implementation of ConfigurationRepository
 * Infrastructure layer adapter
 *
 * For MVP: Uses fixed 500M USD limit
 * For production: Would fetch from external configuration system
 */
@Slf4j
public class InMemoryConfigurationRepository implements ConfigurationRepository {

    private static final BigDecimal DEFAULT_LIMIT = new BigDecimal("500000000.00");

    @Override
    public Future<Optional<BigDecimal>> getExposureLimit(String counterpartyId) {
        // MVP: Return empty to use default limit
        // Production: Would look up counterparty-specific limit
        return Future.succeededFuture(Optional.empty());
    }

    @Override
    public Future<BigDecimal> getDefaultExposureLimit() {
        return Future.succeededFuture(DEFAULT_LIMIT);
    }

    @Override
    public Future<Boolean> shouldIncludeInRunningTotal(String businessStatus, String direction) {
        // Standard filtering rules:
        // Include if: direction = PAY AND businessStatus != CANCELLED
        boolean include = "PAY".equalsIgnoreCase(direction) && !"CANCELLED".equalsIgnoreCase(businessStatus);
        return Future.succeededFuture(include);
    }

    @Override
    public Future<Void> refreshConfiguration() {
        // MVP: No external configuration to refresh
        // Production: Would fetch from external rule system
        log.info("Configuration refresh called (no-op in MVP mode)");
        return Future.succeededFuture();
    }
}
