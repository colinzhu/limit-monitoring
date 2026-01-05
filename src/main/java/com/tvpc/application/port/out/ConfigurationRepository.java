package com.tvpc.application.port.out;

import com.tvpc.domain.model.BusinessStatus;
import com.tvpc.domain.model.SettlementDirection;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Output port for configuration operations
 */
public interface ConfigurationRepository {

    /**
     * Get business statuses that should be included in running total calculations
     */
    Set<BusinessStatus> getIncludedBusinessStatuses();

    /**
     * Get settlement direction that should be included
     */
    SettlementDirection getIncludedDirection();

    /**
     * Get exposure limit for a counterparty
     */
    BigDecimal getExposureLimit(String counterpartyId);

    /**
     * Check if we're in MVP mode (fixed 500M limit) or advanced mode
     */
    boolean isMvpMode();

    /**
     * Refresh rules from external system
     */
    void refreshRules();

    /**
     * Get the time when rules were last refreshed
     */
    long getLastRuleRefreshTime();
}
