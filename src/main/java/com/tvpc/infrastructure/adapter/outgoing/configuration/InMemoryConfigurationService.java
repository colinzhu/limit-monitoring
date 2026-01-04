package com.tvpc.infrastructure.adapter.outgoing.configuration;

import com.tvpc.domain.model.BusinessStatus;
import com.tvpc.domain.model.SettlementDirection;
import com.tvpc.domain.port.configuration.ConfigurationService;
import io.vertx.core.Future;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of ConfigurationService port
 * For MVP: Fixed 500M limit, static rules
 * Later: Can be extended to fetch from external system
 */
public class InMemoryConfigurationService implements ConfigurationService {

    private static final BigDecimal MVP_LIMIT = new BigDecimal("500000000.00"); // 500M USD
    private final AtomicLong lastRefreshTime = new AtomicLong(System.currentTimeMillis());

    @Override
    public Set<BusinessStatus> getIncludedBusinessStatuses() {
        // From requirements: PENDING, INVALID, VERIFIED are included
        return Set.of(BusinessStatus.PENDING, BusinessStatus.INVALID, BusinessStatus.VERIFIED);
    }

    @Override
    public SettlementDirection getIncludedDirection() {
        // Only PAY settlements contribute to exposure
        return SettlementDirection.PAY;
    }

    @Override
    public BigDecimal getExposureLimit(String counterpartyId) {
        // MVP mode: Fixed limit for all counterparties
        // Advanced mode would fetch from external system based on counterpartyId
        return MVP_LIMIT;
    }

    @Override
    public Future<Void> refresh() {
        // In MVP mode, rules are static
        // In advanced mode, this would:
        // 1. Call external rule system API
        // 2. Update cached rules
        // 3. Identify affected groups
        // 4. Trigger recalculation for affected groups
        lastRefreshTime.set(System.currentTimeMillis());
        return Future.succeededFuture();
    }

    @Override
    public long getLastRefreshTime() {
        return lastRefreshTime.get();
    }

    @Override
    public boolean isStale(long maxAgeMillis) {
        return System.currentTimeMillis() - lastRefreshTime.get() > maxAgeMillis;
    }
}

