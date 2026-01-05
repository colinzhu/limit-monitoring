package com.tvpc.adapter.out.persistence;

import com.tvpc.application.port.out.ConfigurationRepository;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;

/**
 * In-memory implementation of ConfigurationRepository
 * For MVP: Fixed 500M limit, static rules
 */
@Slf4j
public class InMemoryConfigurationAdapter implements ConfigurationRepository {

    private static final BigDecimal MVP_LIMIT = new BigDecimal("500000000.00"); // 500M USD

    @Override
    public BigDecimal getExposureLimit(String counterpartyId) {
        return MVP_LIMIT;
    }

    @Override
    public boolean isMvpMode() {
        return true;
    }
}
