package com.tvpc.domain.event;

import lombok.Value;

import java.time.LocalDate;

/**
 * Settlement Event - Domain event for running total recalculation
 * Triggered when a settlement needs running total recalculation
 * Immutable - Value Object
 */
@Value
public class SettlementEvent {
    private final String pts;
    private final String processingEntity;
    private final String counterpartyId;
    private final LocalDate valueDate;
    private final Long refId;  // The settlement's sequence ID

    /**
     * Get the group identifier for this event
     */
    public String getGroupKey() {
        return String.format("%s|%s|%s|%s", pts, processingEntity, counterpartyId, valueDate);
    }
}
