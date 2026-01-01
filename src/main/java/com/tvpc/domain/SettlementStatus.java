package com.tvpc.domain;

/**
 * Settlement status enumeration.
 * Calculated on-demand based on running total, workflow state, and settlement properties.
 */
public enum SettlementStatus {
    /**
     * Settlement is within limit or not a PAY settlement.
     * RECEIVE settlements and CANCELLED settlements always show CREATED.
     */
    CREATED,

    /**
     * PAY settlement with VERIFIED status that exceeds limit.
     * Eligible for approval workflow.
     */
    BLOCKED,

    /**
     * Settlement has been requested for release by one user.
     * Waiting for authorization by a different user.
     */
    PENDING_AUTHORISE,

    /**
     * Settlement has been authorized by a second user.
     * Can proceed despite exceeding limit.
     */
    AUTHORISED;

    /**
     * Checks if status indicates the settlement is blocked.
     */
    public boolean isBlocked() {
        return this == BLOCKED;
    }

    /**
     * Checks if status indicates approval is pending.
     */
    public boolean isPendingApproval() {
        return this == PENDING_AUTHORISE;
    }

    /**
     * Checks if status indicates settlement is authorized.
     */
    public boolean isAuthorized() {
        return this == AUTHORISED;
    }

    /**
     * Checks if status indicates settlement is clear to proceed.
     */
    public boolean isClear() {
        return this == CREATED || this == AUTHORISED;
    }
}
