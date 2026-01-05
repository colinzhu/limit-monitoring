package com.tvpc.application.port.out;

import java.math.BigDecimal;

/**
 * Output port for configuration operations
 */
public interface ConfigurationRepository {

    /**
     * Get exposure limit for a counterparty
     */
    BigDecimal getExposureLimit(String counterpartyId);

    /**
     * Check if we're in MVP mode (fixed 500M limit) or advanced mode
     */
    boolean isMvpMode();
}
