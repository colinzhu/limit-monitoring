package com.tvpc.application.port.outbound;

import com.tvpc.domain.model.Activity;
import io.vertx.core.Future;

import java.util.List;

/**
 * Outbound port - Repository for Activity (audit) records
 * Secondary port (driven by the infrastructure layer)
 */
public interface ActivityRepository {
    /**
     * Save an activity record
     * @param activity The activity to save
     * @return Future indicating completion
     */
    Future<Void> save(Activity activity);

    /**
     * Find activities for a settlement
     * @param settlementId Business settlement ID
     * @param settlementVersion Version number
     * @return Future with list of activities
     */
    Future<List<Activity>> findBySettlement(String settlementId, Long settlementVersion);

    /**
     * Find activities for a settlement (latest version)
     * @param settlementId Business settlement ID
     * @param pts Primary Trading System
     * @param processingEntity Business unit
     * @return Future with list of activities
     */
    Future<List<Activity>> findBySettlementLatest(String settlementId, String pts, String processingEntity);

    /**
     * Check if a user already requested release for a settlement
     * @param settlementId Business settlement ID
     * @param settlementVersion Version number
     * @param userId User ID
     * @return Future with true if already requested
     */
    Future<Boolean> hasUserRequestedRelease(String settlementId, Long settlementVersion, String userId);
}
