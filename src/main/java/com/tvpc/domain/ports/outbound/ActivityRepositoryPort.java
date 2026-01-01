package com.tvpc.domain.ports.outbound;

import com.tvpc.domain.WorkflowInfo;
import io.vertx.core.Future;

/**
 * Outbound port for activity/audit trail operations.
 */
public interface ActivityRepositoryPort {

    /**
     * Records an activity in the audit trail.
     *
     * @param pts The trading system identifier
     * @param processingEntity The processing entity
     * @param settlementId The settlement identifier
     * @param settlementVersion The settlement version
     * @param userId The user identifier
     * @param userName The user name
     * @param actionType The action type (e.g., "REQUEST_RELEASE", "AUTHORISE")
     * @param actionComment Optional comment
     * @return Void future
     */
    Future<Void> recordActivity(
            String pts,
            String processingEntity,
            String settlementId,
            Long settlementVersion,
            String userId,
            String userName,
            String actionType,
            String actionComment
    );

    /**
     * Checks if a user has already requested release for a settlement.
     *
     * @param settlementId The settlement identifier
     * @param settlementVersion The settlement version
     * @param userId The user identifier
     * @return True if user has requested
     */
    Future<Boolean> hasUserRequested(String settlementId, Long settlementVersion, String userId);

    /**
     * Checks if a settlement has been authorized.
     *
     * @param settlementId The settlement identifier
     * @param settlementVersion The settlement version
     * @return True if authorized
     */
    Future<Boolean> isAuthorized(String settlementId, Long settlementVersion);

    /**
     * Gets workflow information for a settlement.
     *
     * @param settlementId The settlement identifier
     * @param settlementVersion The settlement version
     * @return Workflow information
     */
    Future<WorkflowInfo> getWorkflowInfo(String settlementId, Long settlementVersion);
}
