package com.tvpc.domain.model;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Settlement - Core domain entity representing a financial transaction
 * Entity - Has identity and lifecycle
 * Immutable - All fields are final, creating new instances for state changes
 */
@Value
@Builder
public class Settlement {
    // Identity
    private final Long id;                    // Auto-generated sequence ID (REF_ID)
    private final String settlementId;        // Business identifier
    private final Long settlementVersion;     // Timestamp in long format from external system

    // Grouping
    private final String pts;                 // Primary Trading System
    private final String processingEntity;    // Business unit within trading system
    private final String counterpartyId;      // External party identifier
    private final LocalDate valueDate;        // Settlement date

    // Transaction
    private final String currency;            // ISO 4217 currency code
    private final BigDecimal amount;          // Transaction amount
    private final BusinessStatus businessStatus;  // PENDING, INVALID, VERIFIED, CANCELLED
    private final SettlementDirection direction;  // PAY or RECEIVE
    private final SettlementType settlementType;  // GROSS or NET

    // Versioning
    private final Boolean isOld;              // Flag for old versions

    // Audit
    private final LocalDateTime createTime;   // Audit timestamp
    private final LocalDateTime updateTime;   // Audit timestamp

    // ===== Domain Logic Methods =====

    /**
     * Check if this settlement contributes to risk exposure
     * @return true if PAY direction and not CANCELLED
     */
    public boolean isIncludedInRunningTotal() {
        return isPay() && !isCancelled();
    }

    /**
     * Check if this is a PAY settlement
     */
    public boolean isPay() {
        return SettlementDirection.PAY.equals(direction);
    }

    /**
     * Check if this is a RECEIVE settlement
     */
    public boolean isReceive() {
        return SettlementDirection.RECEIVE.equals(direction);
    }

    /**
     * Check if this settlement is cancelled
     */
    public boolean isCancelled() {
        return BusinessStatus.CANCELLED.equals(businessStatus);
    }

    /**
     * Check if this settlement is verified
     */
    public boolean isVerified() {
        return BusinessStatus.VERIFIED.equals(businessStatus);
    }

    /**
     * Check if this settlement is eligible for approval workflow
     * Must be PAY, VERIFIED, and not CANCELLED
     */
    public boolean isEligibleForApproval() {
        return isPay() && isVerified() && !isCancelled();
    }

    /**
     * Get the group identifier for running total calculation
     * Group = PTS + Processing Entity + Counterparty + Value Date
     */
    public String getGroupKey() {
        return String.format("%s|%s|%s|%s", pts, processingEntity, counterpartyId, valueDate);
    }

    /**
     * Create a new version of this settlement with updated fields
     * Returns a new Settlement instance (immutable pattern)
     */
    public Settlement withUpdatedStatus(BusinessStatus newStatus) {
        return Settlement.builder()
                .id(this.id)
                .settlementId(this.settlementId)
                .settlementVersion(this.settlementVersion)
                .pts(this.pts)
                .processingEntity(this.processingEntity)
                .counterpartyId(this.counterpartyId)
                .valueDate(this.valueDate)
                .currency(this.currency)
                .amount(this.amount)
                .businessStatus(newStatus)
                .direction(this.direction)
                .settlementType(this.settlementType)
                .isOld(this.isOld)
                .createTime(this.createTime)
                .updateTime(LocalDateTime.now())
                .build();
    }

    /**
     * Mark this settlement as old version
     */
    public Settlement markAsOld() {
        return Settlement.builder()
                .id(this.id)
                .settlementId(this.settlementId)
                .settlementVersion(this.settlementVersion)
                .pts(this.pts)
                .processingEntity(this.processingEntity)
                .counterpartyId(this.counterpartyId)
                .valueDate(this.valueDate)
                .currency(this.currency)
                .amount(this.amount)
                .businessStatus(this.businessStatus)
                .direction(this.direction)
                .settlementType(this.settlementType)
                .isOld(true)
                .createTime(this.createTime)
                .updateTime(LocalDateTime.now())
                .build();
    }

    /**
     * Check if this settlement has the same group as another
     */
    public boolean isSameGroup(Settlement other) {
        return this.pts.equals(other.pts) &&
               this.processingEntity.equals(other.processingEntity) &&
               this.counterpartyId.equals(other.counterpartyId) &&
               this.valueDate.equals(other.valueDate);
    }

    /**
     * Check if counterparty changed compared to another settlement
     */
    public boolean hasCounterpartyChanged(Settlement other) {
        return !this.counterpartyId.equals(other.counterpartyId);
    }

    /**
     * Check if direction changed compared to another settlement
     */
    public boolean hasDirectionChanged(Settlement other) {
        return !this.direction.equals(other.direction);
    }

    /**
     * Check if this is a NET settlement that could change direction
     */
    public boolean isPotentiallyDirectionChanging() {
        return SettlementType.NET.equals(settlementType);
    }
}
