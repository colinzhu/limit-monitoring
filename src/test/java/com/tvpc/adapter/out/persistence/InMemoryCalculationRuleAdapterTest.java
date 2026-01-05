package com.tvpc.adapter.out.persistence;

import com.tvpc.adapter.out.http.CalculationRuleHttpAdapter;
import com.tvpc.domain.model.BusinessStatus;
import com.tvpc.domain.model.CalculationRule;
import com.tvpc.domain.model.SettlementDirection;
import com.tvpc.domain.model.SettlementType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryCalculationRuleAdapterTest {

    private Vertx vertx;
    private InMemoryCalculationRuleAdapter adapter;
    private CalculationRuleHttpAdapter ruleHttpAdapter;

    @BeforeEach
    void setUp() {
        vertx = Vertx.vertx();
        ruleHttpAdapter = new CalculationRuleHttpAdapter();
        adapter = new InMemoryCalculationRuleAdapter(vertx, ruleHttpAdapter);
    }

    @AfterEach
    void tearDown() {
        if (vertx != null) {
            vertx.close();
        }
    }

    @Test
    void testGetCalculationRule_existingRule() {
        // Wait for initial load
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Optional<CalculationRule> ruleOpt = adapter.getCalculationRule("PTS-A", "PE-001");

        assertTrue(ruleOpt.isPresent());
        CalculationRule rule = ruleOpt.get();
        assertEquals("PTS-A", rule.getPts());
        assertEquals("PE-001", rule.getProcessingEntity());
        assertFalse(rule.getIncludedBusinessStatuses().isEmpty());
        assertFalse(rule.getIncludedDirections().isEmpty());
        assertFalse(rule.getIncludedSettlementTypes().isEmpty());
    }

    @Test
    void testGetCalculationRule_nonExistingRule_returnsDefault() {
        Optional<CalculationRule> ruleOpt = adapter.getCalculationRule("PTS-X", "PE-999");

        assertTrue(ruleOpt.isPresent());
        CalculationRule rule = ruleOpt.get();
        assertEquals("PTS-X", rule.getPts());
        assertEquals("PE-999", rule.getProcessingEntity());
        // Should have default values
        assertTrue(rule.getIncludedBusinessStatuses().contains(BusinessStatus.PENDING));
        assertTrue(rule.getIncludedDirections().contains(SettlementDirection.PAY));
    }

    @Test
    void testInitialize_loadsRules() {
        Future<Void> future = adapter.initialize();

        assertTrue(future.succeeded());
        
        // Verify rules are cached after initialization
        Optional<CalculationRule> ruleOpt = adapter.getCalculationRule("PTS-A", "PE-001");
        assertTrue(ruleOpt.isPresent());
    }
}
