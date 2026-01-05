package com.tvpc.adapter.out.http;

import com.tvpc.domain.model.BusinessStatus;
import com.tvpc.domain.model.CalculationRule;
import com.tvpc.domain.model.SettlementDirection;
import com.tvpc.domain.model.SettlementType;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalculationRuleHttpAdapterTest {

    private CalculationRuleHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new CalculationRuleHttpAdapter();
    }

    @Test
    void testFetchRules_returnsDummyData() {
        Future<List<CalculationRule>> future = adapter.fetchRules();

        assertTrue(future.succeeded());
        List<CalculationRule> rules = future.result();
        
        assertNotNull(rules);
        assertFalse(rules.isEmpty());
        assertTrue(rules.size() >= 3);
    }

    @Test
    void testFetchRules_containsExpectedRules() {
        Future<List<CalculationRule>> future = adapter.fetchRules();
        List<CalculationRule> rules = future.result();

        // Verify PTS-A / PE-001 rule
        CalculationRule rule1 = rules.stream()
                .filter(r -> "PTS-A".equals(r.getPts()) && "PE-001".equals(r.getProcessingEntity()))
                .findFirst()
                .orElse(null);

        assertNotNull(rule1);
        assertTrue(rule1.getIncludedBusinessStatuses().contains(BusinessStatus.PENDING));
        assertTrue(rule1.getIncludedBusinessStatuses().contains(BusinessStatus.VERIFIED));
        assertTrue(rule1.getIncludedDirections().contains(SettlementDirection.PAY));
        assertTrue(rule1.getIncludedSettlementTypes().contains(SettlementType.GROSS));
        assertTrue(rule1.getIncludedSettlementTypes().contains(SettlementType.NET));
    }

    @Test
    void testFetchRules_eachRuleHasRequiredFields() {
        Future<List<CalculationRule>> future = adapter.fetchRules();
        List<CalculationRule> rules = future.result();

        for (CalculationRule rule : rules) {
            assertNotNull(rule.getPts());
            assertNotNull(rule.getProcessingEntity());
            assertNotNull(rule.getIncludedBusinessStatuses());
            assertNotNull(rule.getIncludedDirections());
            assertNotNull(rule.getIncludedSettlementTypes());
            assertFalse(rule.getIncludedBusinessStatuses().isEmpty());
            assertFalse(rule.getIncludedDirections().isEmpty());
            assertFalse(rule.getIncludedSettlementTypes().isEmpty());
        }
    }
}
