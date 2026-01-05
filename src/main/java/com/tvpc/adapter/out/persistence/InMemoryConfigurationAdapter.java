package com.tvpc.adapter.out.persistence;

import com.tvpc.application.port.out.ConfigurationRepository;
import com.tvpc.domain.model.BusinessStatus;
import com.tvpc.domain.model.SettlementDirection;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory implementation of ConfigurationRepository
 * For MVP: Fixed 500M limit, static rules
 */
@Slf4j
public class InMemoryConfigurationAdapter implements ConfigurationRepository {

    private static final BigDecimal MVP_LIMIT = new BigDecimal("500000000.00"); // 500M USD
    private final AtomicLong lastRefreshTime = new AtomicLong(System.currentTimeMillis());

    @Override
    public Set<BusinessStatus> getIncludedBusinessStatuses() {
        return Set.of(BusinessStatus.PENDING, BusinessStatus.INVALID, BusinessStatus.VERIFIED);
    }

    @Override
    public SettlementDirection getIncludedDirection() {
        return SettlementDirection.PAY;
    }

    @Override
    public BigDecimal getExposureLimit(String counterpartyId) {
        return MVP_LIMIT;
    }

    @Override
    public boolean isMvpMode() {
        return true;
    }

    @Override
    public void refreshRules() {
        lastRefreshTime.set(System.currentTimeMillis());
    }

    @Override
    public long getLastRuleRefreshTime() {
        return lastRefreshTime.get();
    }
}
