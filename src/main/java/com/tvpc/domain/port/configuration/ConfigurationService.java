package com.tvpc.domain.port.configuration;

import com.tvpc.domain.model.BusinessStatus;
import com.tvpc.domain.model.SettlementDirection;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Configuration service port for filtering rules and limits
 * This is a domain port (interface) that defines the contract for configuration.
 * Infrastructure layer will provide the implementation (in-memory, database, external system, etc.).
 */
public interface ConfigurationService {

    /**
     * Get business statuses that should be included in running total calculations
     * Default: PENDING, INVALID, VERIFIED
     * @return Set of business statuses
     */
    Set<BusinessStatus> getIncludedBusinessStatuses();

    /**
     * Get settlement direction that should be included
     * Default: PAY
     * @return Settlement direction
     */
    SettlementDirection getIncludedDirection();

    /**
     * Get exposure limit for a counterparty
     * @param counterpartyId Counterparty identifier
     * @return Exposure limit in USD
     */
    BigDecimal getExposureLimit(String counterpartyId);

    /**
     * Refresh configuration from source
     * For external systems, this will fetch the latest configuration
     * @return Future indicating completion
     */
    io.vertx.core.Future<Void> refresh();

    /**
     * Get last refresh timestamp
     * @return Last refresh time in milliseconds since epoch
     */
    long getLastRefreshTime();

    /**
     * Check if configuration is stale and needs refresh
     * @param maxAgeMillis Maximum age in milliseconds
     * @return True if configuration is stale
     */
    boolean isStale(long maxAgeMillis);
}
