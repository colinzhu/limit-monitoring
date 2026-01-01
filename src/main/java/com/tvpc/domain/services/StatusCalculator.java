package com.tvpc.domain.services;

import com.tvpc.domain.Settlement;
import com.tvpc.domain.SettlementStatus;
import com.tvpc.domain.WorkflowInfo;
import com.tvpc.domain.SettlementDirection;
import com.tvpc.domain.BusinessStatus;

import java.math.BigDecimal;

/**
 * Domain service for calculating settlement status on-demand.
 * Implements the status calculation logic without storing status.
 */
public class StatusCalculator {

    /**
     * Calculates the current status of a settlement.
     *
     * <p>Status calculation rules:
     * <ul>
     *   <li>RECEIVE or CANCELLED → CREATED</li>
     *   <li>Running total ≤ limit → CREATED</li>
     *   <li>Running total > limit + PAY + VERIFIED → BLOCKED</li>
     *   <li>Has PENDING_AUTHORISE workflow → PENDING_AUTHORISE</li>
     *   <li>Has AUTHORISE after PENDING → AUTHORISED</li>
     * </ul>
     *
     * @param settlement The settlement to calculate status for
     * @param runningTotal The group's running total in USD
     * @param limit The exposure limit in USD
     * @param workflow The workflow information for this settlement
     * @return The calculated settlement status
     */
    public SettlementStatus calculateStatus(
            Settlement settlement,
            BigDecimal runningTotal,
            BigDecimal limit,
            WorkflowInfo workflow
    ) {
        // Rule 1: RECEIVE or CANCELLED settlements always show CREATED
        if (settlement.getDirection() == SettlementDirection.RECEIVE ||
            settlement.getBusinessStatus() == BusinessStatus.CANCELLED) {
            return SettlementStatus.CREATED;
        }

        // Rule 2: If running total is within limit, status is CREATED
        if (runningTotal.compareTo(limit) <= 0) {
            return SettlementStatus.CREATED;
        }

        // At this point: running total > limit

        // Rule 3: If workflow shows authorized, status is AUTHORISED
        if (workflow != null && workflow.isAuthorized()) {
            return SettlementStatus.AUTHORISED;
        }

        // Rule 4: If workflow shows pending authorise, status is PENDING_AUTHORISE
        if (workflow != null && workflow.isPendingAuthorise()) {
            return SettlementStatus.PENDING_AUTHORISE;
        }

        // Rule 5: Must be PAY + VERIFIED to be BLOCKED
        // (Other statuses would have been caught by earlier rules)
        if (settlement.getDirection() == SettlementDirection.PAY &&
            settlement.getBusinessStatus() == BusinessStatus.VERIFIED) {
            return SettlementStatus.BLOCKED;
        }

        // Default for any other case
        return SettlementStatus.CREATED;
    }

    /**
     * Simplified status calculation without workflow info.
     * Used when workflow information is not available.
     */
    public SettlementStatus calculateStatus(
            Settlement settlement,
            BigDecimal runningTotal,
            BigDecimal limit
    ) {
        return calculateStatus(settlement, runningTotal, limit, null);
    }

    /**
     * Checks if a settlement is eligible for approval workflow.
     *
     * @param settlement The settlement to check
     * @param runningTotal The group's running total
     * @param limit The exposure limit
     * @return True if eligible for approval
     */
    public boolean isEligibleForApproval(
            Settlement settlement,
            BigDecimal runningTotal,
            BigDecimal limit
    ) {
        // Must be PAY
        if (settlement.getDirection() != SettlementDirection.PAY) {
            return false;
        }

        // Must be VERIFIED
        if (settlement.getBusinessStatus() != BusinessStatus.VERIFIED) {
            return false;
        }

        // Must exceed limit
        if (runningTotal.compareTo(limit) <= 0) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a settlement should be included in running total calculations.
     *
     * @param settlement The settlement to check
     * @return True if included
     */
    public boolean isIncludedInRunningTotal(Settlement settlement) {
        return settlement.getDirection() == SettlementDirection.PAY &&
               settlement.getBusinessStatus() != BusinessStatus.CANCELLED;
    }
}
