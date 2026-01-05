package com.tvpc.adapter.out.http;

import com.tvpc.domain.model.BusinessStatus;
import com.tvpc.domain.model.CalculationRule;
import com.tvpc.domain.model.SettlementDirection;
import com.tvpc.domain.model.SettlementType;
import io.vertx.core.Future;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * HTTP adapter to fetch calculation rules from external system
 * Currently returns dummy test data
 */
@Slf4j
public class CalculationRuleHttpAdapter {

    /**
     * Fetch calculation rules from external system
     * TODO: Replace with actual HTTP call when external system is ready
     */
    public Future<List<CalculationRule>> fetchRules() {
        log.info("Fetching calculation rules from external system (dummy data)");

        // Dummy test data
        List<CalculationRule> rules = List.of(
                CalculationRule.builder()
                        .pts("PTS-A")
                        .processingEntity("PE-001")
                        .includedBusinessStatuses(Set.of(BusinessStatus.PENDING, BusinessStatus.VERIFIED))
                        .includedDirections(Set.of(SettlementDirection.PAY))
                        .includedSettlementTypes(Set.of(SettlementType.GROSS, SettlementType.NET))
                        .build(),
                CalculationRule.builder()
                        .pts("PTS-B")
                        .processingEntity("PE-002")
                        .includedBusinessStatuses(Set.of(BusinessStatus.VERIFIED))
                        .includedDirections(Set.of(SettlementDirection.PAY))
                        .includedSettlementTypes(Set.of(SettlementType.GROSS))
                        .build(),
                CalculationRule.builder()
                        .pts("PTS-A")
                        .processingEntity("PE-002")
                        .includedBusinessStatuses(Set.of(BusinessStatus.PENDING, BusinessStatus.INVALID, BusinessStatus.VERIFIED))
                        .includedDirections(Set.of(SettlementDirection.PAY, SettlementDirection.RECEIVE))
                        .includedSettlementTypes(Set.of(SettlementType.GROSS))
                        .build()
        );

        return Future.succeededFuture(rules);
    }
}
