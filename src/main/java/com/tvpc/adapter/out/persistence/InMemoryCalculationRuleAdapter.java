package com.tvpc.adapter.out.persistence;

import com.tvpc.adapter.out.http.CalculationRuleHttpAdapter;
import com.tvpc.application.port.out.CalculationRuleRepository;
import com.tvpc.domain.model.BusinessStatus;
import com.tvpc.domain.model.CalculationRule;
import com.tvpc.domain.model.SettlementDirection;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.tvpc.domain.model.SettlementType.GROSS;
import static com.tvpc.domain.model.SettlementType.NET;

/**
 * In-memory implementation of CalculationRuleRepository with rule caching
 * Fetches rules from external system and refreshes every 5 minutes
 */
@Slf4j
public class InMemoryCalculationRuleAdapter implements CalculationRuleRepository {

    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    private final AtomicLong lastRefreshTime = new AtomicLong(System.currentTimeMillis());
    private final Map<String, CalculationRule> ruleCache = new ConcurrentHashMap<>();
    private final CalculationRuleHttpAdapter ruleHttpAdapter;
    private final Vertx vertx;

    public InMemoryCalculationRuleAdapter(Vertx vertx, CalculationRuleHttpAdapter ruleHttpAdapter) {
        this.vertx = vertx;
        this.ruleHttpAdapter = ruleHttpAdapter;
        
        // Schedule periodic refresh every 5 minutes
        vertx.setPeriodic(REFRESH_INTERVAL_MS, id -> {
            log.info("Periodic refresh triggered");
            refreshRules()
                    .onSuccess(v -> log.info("Periodic refresh completed successfully"))
                    .onFailure(error -> log.error("Periodic refresh failed, keeping cached rules: {}", error.getMessage()));
        });
    }
    
    /**
     * Initialize rules - must be called after construction and before app starts
     * This is a critical operation that must succeed for the app to start
     */
    public Future<Void> initialize() {
        log.info("Initializing calculation rules (critical for startup)");
        return refreshRules()
                .onSuccess(v -> log.info("Calculation rules initialized successfully"))
                .onFailure(error -> log.error("CRITICAL: Failed to initialize calculation rules", error));
    }

    @Override
    public Optional<CalculationRule> getCalculationRule(String pts, String processingEntity) {
        String key = buildKey(pts, processingEntity);
        CalculationRule rule = ruleCache.get(key);
        
        if (rule == null) {
            log.warn("No calculation rule found for pts={}, processingEntity={}, using default", pts, processingEntity);
            // Return default rule if not found
            return Optional.of(CalculationRule.builder()
                    .pts(pts)
                    .processingEntity(processingEntity)
                    .includedBusinessStatuses(Set.of(BusinessStatus.PENDING, BusinessStatus.INVALID, BusinessStatus.VERIFIED))
                    .includedDirections(Set.of(SettlementDirection.PAY))
                    .includedSettlementTypes(Set.of(GROSS, NET))
                    .build());
        }
        
        return Optional.of(rule);
    }

    /**
     * Refresh rules from external system (private - only used internally)
     */
    private Future<Void> refreshRules() {
        log.info("Refreshing calculation rules from external system");
        
        return ruleHttpAdapter.fetchRules()
                .onSuccess(rules -> {
                    // Update cache
                    ruleCache.clear();
                    for (CalculationRule rule : rules) {
                        String key = buildKey(rule.getPts(), rule.getProcessingEntity());
                        ruleCache.put(key, rule);
                        log.debug("Cached rule for pts={}, processingEntity={}", rule.getPts(), rule.getProcessingEntity());
                    }
                    lastRefreshTime.set(System.currentTimeMillis());
                    log.info("Successfully refreshed {} calculation rules", rules.size());
                })
                .onFailure(error -> {
                    log.error("Failed to refresh rules: {}", error.getMessage(), error);
                    // Keep existing cache on failure
                })
                .mapEmpty();
    }

    private String buildKey(String pts, String processingEntity) {
        return pts + ":" + processingEntity;
    }
}
