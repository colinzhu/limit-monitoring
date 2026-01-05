package com.tvpc.domain.model;

import lombok.Builder;
import lombok.Value;

import java.util.Set;

/**
 * Domain model for calculation rules
 * Each PTS+ProcessingEntity has its own configuration
 */
@Value
@Builder
public class CalculationRule {
    String pts;
    String processingEntity;
    Set<BusinessStatus> includedBusinessStatuses;
    Set<SettlementDirection> includedDirections;
    Set<SettlementType> includedSettlementTypes;
}
