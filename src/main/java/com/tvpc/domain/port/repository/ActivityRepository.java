package com.tvpc.domain.port.repository;

import io.vertx.core.Future;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Repository port for Activity/Audit trail operations
 * This is a domain port (interface) that defines the contract for activity persistence.
 * Infrastructure layer will provide the implementation.
 */
public interface ActivityRepository {

    /**
     * Record an activity
     * @param pts Primary Trading System
     * @param processingEntity Business unit
     * @param settlementId Settlement identifier
     * @param settlementVersion Settlement version
     * @param userId User identifier
     * @param userName User name
     * @param actionType Action type (REQUEST_RELEASE, AUTHORISE, etc.)
     * @param actionComment Optional comment
     * @return Future indicating completion
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
     * @param settlementId Settlement identifier
     * @param settlementVersion Settlement version
     * @param userId User identifier
     * @return Future with true if user already requested
     */
    Future<Boolean> hasUserRequested(String settlementId, Long settlementVersion, String userId);

    /**
     * Check if a settlement has been authorized
     * @param settlementId Settlement identifier
     * @param settlementVersion Settlement version
     * @return Future with true if authorized
     */
    Future<Boolean> isAuthorized(String settlementId, Long settlementVersion);

    /**
     * Get approval workflow info for a settlement
     * @param settlementId Settlement identifier
     * @param settlementVersion Settlement version
     * @return Future with workflow info (requester, authorizer, timestamps)
     */
    Future<WorkflowInfo> getWorkflowInfo(String settlementId, Long settlementVersion);

    /**
     * Workflow information value object
     */
    @Value
    class WorkflowInfo {
        String requesterId;
        String requesterName;
        LocalDateTime requestTime;
        String authorizerId;
        String authorizerName;
        LocalDateTime authorizeTime;
    }
}


