package com.tvpc.application.port.out;

import com.tvpc.domain.model.CalculationRule;

import java.util.Optional;

/**
 * Output port for calculation rule operations
 */
public interface CalculationRuleRepository {

    /**
     * Get calculation rule for a specific PTS and ProcessingEntity
     */
    Optional<CalculationRule> getCalculationRule(String pts, String processingEntity);
}
