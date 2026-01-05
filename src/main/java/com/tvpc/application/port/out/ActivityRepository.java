package com.tvpc.application.port.out;

import io.vertx.core.Future;

import java.time.LocalDateTime;

/**
 * Output port for activity/audit trail persistence operations
 */
public interface ActivityRepository {

    /**
     * Record an activity
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
     * Check if a user has already requested release for a settlement
     */
    Future<Boolean> hasUserRequested(String settlementId, Long settlementVersion, String userId);

    /**
     * Check if a settlement has been authorized
     */
    Future<Boolean> isAuthorized(String settlementId, Long settlementVersion);

    /**
     * Get approval workflow info for a settlement
     */
    Future<WorkflowInfo> getWorkflowInfo(String settlementId, Long settlementVersion);

    /**
     * Workflow information record
     */
    record WorkflowInfo(
            String requesterId,
            String requesterName,
            LocalDateTime requestTime,
            String authorizerId,
            String authorizerName,
            LocalDateTime authorizeTime
    ) {}
}
