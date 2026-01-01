package com.tvpc.infrastructure.config;

import com.tvpc.domain.ports.outbound.ConfigurationServicePort;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of ConfigurationServicePort.
 * Manages application configuration and limits.
 * Can be replaced with database-backed or external config service.
 */
public class InMemoryConfigurationService implements ConfigurationServicePort {

    private final Map<String, Object> configMap;

    public InMemoryConfigurationService() {
        this.configMap = new ConcurrentHashMap<>();
        initializeDefaultConfig();
    }

    public InMemoryConfigurationService(JsonObject initialConfig) {
        this.configMap = new ConcurrentHashMap<>();
        // Load from JsonObject
        for (String key : initialConfig.fieldNames()) {
            configMap.put(key, initialConfig.getValue(key));
        }
        // Ensure defaults are set
        initializeDefaultConfig();
    }

    private void initializeDefaultConfig() {
        // MVP mode - fixed 500M limit
        configMap.putIfAbsent("mvp_mode", true);
        configMap.putIfAbsent("fixed_limit", new BigDecimal("500000000.00"));

        // Default filtering rules
        configMap.putIfAbsent("include_pay_direction", true);
        configMap.putIfAbsent("include_receive_direction", false);
        configMap.putIfAbsent("excluded_statuses", new String[]{"CANCELLED"});
    }

    @Override
    public Future<BigDecimal> getExposureLimit(String counterpartyId) {
        Promise<BigDecimal> promise = Promise.promise();
        // In MVP mode, all counterparties share the fixed limit
        BigDecimal limit = (BigDecimal) configMap.get("fixed_limit");
        promise.complete(limit != null ? limit : new BigDecimal("500000000.00"));
        return promise.future();
    }

    @Override
    public Future<Boolean> shouldIncludeInCalculation(String direction, String businessStatus) {
        Promise<Boolean> promise = Promise.promise();

        Boolean includePay = (Boolean) configMap.get("include_pay_direction");
        Boolean includeReceive = (Boolean) configMap.get("include_receive_direction");
        String[] excludedStatuses = (String[]) configMap.get("excluded_statuses");

        if (excludedStatuses == null) {
            excludedStatuses = new String[]{"CANCELLED"};
        }

        // Check direction
        boolean directionAllowed = ("PAY".equals(direction) && (includePay == null || includePay)) ||
                                   ("RECEIVE".equals(direction) && (includeReceive != null && includeReceive));

        // Check status
        boolean statusAllowed = true;
        for (String excluded : excludedStatuses) {
            if (excluded.equals(businessStatus)) {
                statusAllowed = false;
                break;
            }
        }

        promise.complete(directionAllowed && statusAllowed);
        return promise.future();
    }

    @Override
    public Future<Void> updateRules() {
        // For MVP, rules are static
        Promise<Void> promise = Promise.promise();
        promise.complete();
        return promise.future();
    }

    @Override
    public Future<Void> updateExchangeRates() {
        // For MVP, rates are manually updated
        Promise<Void> promise = Promise.promise();
        promise.complete();
        return promise.future();
    }
}
