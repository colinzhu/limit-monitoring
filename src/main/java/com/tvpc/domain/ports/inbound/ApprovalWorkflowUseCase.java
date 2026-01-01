package com.tvpc.domain.ports.inbound;

import io.vertx.core.Future;

/**
 * Inbound port for approval workflow use case.
 * Handles two-step approval process with user segregation.
 */
public interface ApprovalWorkflowUseCase {

    /**
     * Requests release for a blocked settlement.
     * Changes status from BLOCKED to PENDING_AUTHORISE.
     *
     * @param settlementId The settlement identifier
     * @param settlementVersion The settlement version
     * @param userId The user identifier (must be different from authorizer)
     * @param userName The user name for audit
     * @param comment Optional comment
     * @return Void future
     */
    Future<Void> requestRelease(
            String settlementId,
            Long settlementVersion,
            String userId,
            String userName,
            String comment
    );

    /**
     * Authorizes a settlement for release.
     * Changes status from PENDING_AUTHORISE to AUTHORISED.
     * Must be called by a different user than who requested.
     *
     * @param settlementId The settlement identifier
     * @param settlementVersion The settlement version
     * @param userId The user identifier (must be different from requester)
     * @param userName The user name for audit
     * @param comment Optional comment
     * @return Void future
     */
    Future<Void> authorize(
            String settlementId,
            Long settlementVersion,
            String userId,
            String userName,
            String comment
    );
}
