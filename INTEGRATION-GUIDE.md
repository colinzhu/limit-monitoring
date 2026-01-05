# Integration Guide: Connecting to Real External System

## Overview
This guide explains how to replace the dummy data in `CalculationRuleHttpAdapter` with actual HTTP calls to the external system.

## Prerequisites
1. External system URL and endpoint
2. Authentication credentials (if required)
3. API contract/specification
4. Network connectivity from application to external system

## Step 1: Add Configuration

### Update `application.yml`
```yaml
http:
  port: 8081

database:
  url: jdbc:oracle:thin:@//localhost:1521/FREEPDB1
  driver_class: oracle.jdbc.OracleDriver
  user: tvpc
  password: tvpc123

# Add this section
calculation-rules:
  external-url: "https://external-system.company.com"
  endpoint: "/api/v1/calculation-rules"
  timeout-ms: 5000
  retry-attempts: 3
  # Optional: if authentication required
  api-key: "${CALC_RULES_API_KEY}"
```

## Step 2: Update CalculationRuleHttpAdapter

### Current Implementation (Dummy Data)
```java
public Future<List<CalculationRule>> fetchRules() {
    log.info("Fetching calculation rules from external system (dummy data)");
    
    // Dummy test data
    List<CalculationRule> rules = List.of(...);
    
    return Future.succeededFuture(rules);
}
```

### New Implementation (Real HTTP Call)

```java
package com.tvpc.adapter.out.http;

import com.tvpc.domain.model.BusinessStatus;
import com.tvpc.domain.model.CalculationRule;
import com.tvpc.domain.model.SettlementDirection;
import com.tvpc.domain.model.SettlementType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HTTP adapter to fetch calculation rules from external system
 */
@Slf4j
public class CalculationRuleHttpAdapter {

    private final WebClient webClient;
    private final String externalUrl;
    private final String endpoint;
    private final String apiKey;
    private final int timeoutMs;

    public CalculationRuleHttpAdapter(Vertx vertx, JsonObject config) {
        this.externalUrl = config.getString("external-url");
        this.endpoint = config.getString("endpoint", "/api/v1/calculation-rules");
        this.apiKey = config.getString("api-key");
        this.timeoutMs = config.getInteger("timeout-ms", 5000);

        WebClientOptions options = new WebClientOptions()
                .setDefaultHost(extractHost(externalUrl))
                .setDefaultPort(extractPort(externalUrl))
                .setSsl(externalUrl.startsWith("https"))
                .setTrustAll(false) // Set to true only for dev/test
                .setConnectTimeout(timeoutMs)
                .setIdleTimeout(timeoutMs);

        this.webClient = WebClient.create(vertx, options);
        
        log.info("CalculationRuleHttpAdapter initialized: url={}, endpoint={}", externalUrl, endpoint);
    }

    /**
     * Fetch calculation rules from external system
     */
    public Future<List<CalculationRule>> fetchRules() {
        log.info("Fetching calculation rules from external system: {}{}", externalUrl, endpoint);

        var request = webClient.get(endpoint);
        
        // Add authentication header if API key is configured
        if (apiKey != null && !apiKey.isEmpty()) {
            request.putHeader("X-API-Key", apiKey);
        }

        return request.send()
                .compose(response -> {
                    if (response.statusCode() == 200) {
                        JsonArray rulesJson = response.bodyAsJsonArray();
                        List<CalculationRule> rules = parseRules(rulesJson);
                        log.info("Successfully fetched {} calculation rules", rules.size());
                        return Future.succeededFuture(rules);
                    } else {
                        String error = String.format("Failed to fetch rules: HTTP %d - %s", 
                                response.statusCode(), response.bodyAsString());
                        log.error(error);
                        return Future.failedFuture(error);
                    }
                })
                .recover(error -> {
                    log.error("Error fetching calculation rules: {}", error.getMessage(), error);
                    return Future.failedFuture(error);
                });
    }

    /**
     * Parse JSON response to CalculationRule objects
     * 
     * Expected JSON format:
     * [
     *   {
     *     "pts": "PTS-A",
     *     "processingEntity": "PE-001",
     *     "includedBusinessStatuses": ["PENDING", "VERIFIED"],
     *     "includedDirections": ["PAY"],
     *     "includedSettlementTypes": ["GROSS", "NET"]
     *   },
     *   ...
     * ]
     */
    private List<CalculationRule> parseRules(JsonArray rulesJson) {
        List<CalculationRule> rules = new ArrayList<>();

        for (int i = 0; i < rulesJson.size(); i++) {
            JsonObject ruleJson = rulesJson.getJsonObject(i);
            
            try {
                CalculationRule rule = CalculationRule.builder()
                        .pts(ruleJson.getString("pts"))
                        .processingEntity(ruleJson.getString("processingEntity"))
                        .includedBusinessStatuses(parseBusinessStatuses(ruleJson.getJsonArray("includedBusinessStatuses")))
                        .includedDirections(parseDirections(ruleJson.getJsonArray("includedDirections")))
                        .includedSettlementTypes(parseSettlementTypes(ruleJson.getJsonArray("includedSettlementTypes")))
                        .build();
                
                rules.add(rule);
                log.debug("Parsed rule: pts={}, pe={}", rule.getPts(), rule.getProcessingEntity());
                
            } catch (Exception e) {
                log.error("Failed to parse rule at index {}: {}", i, e.getMessage(), e);
                // Continue parsing other rules
            }
        }

        return rules;
    }

    private Set<BusinessStatus> parseBusinessStatuses(JsonArray array) {
        Set<BusinessStatus> statuses = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            String value = array.getString(i);
            statuses.add(BusinessStatus.fromValue(value));
        }
        return statuses;
    }

    private Set<SettlementDirection> parseDirections(JsonArray array) {
        Set<SettlementDirection> directions = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            String value = array.getString(i);
            directions.add(SettlementDirection.fromValue(value));
        }
        return directions;
    }

    private Set<SettlementType> parseSettlementTypes(JsonArray array) {
        Set<SettlementType> types = new HashSet<>();
        for (int i = 0; i < array.size(); i++) {
            String value = array.getString(i);
            types.add(SettlementType.fromValue(value));
        }
        return types;
    }

    private String extractHost(String url) {
        // Remove protocol
        String withoutProtocol = url.replaceFirst("^https?://", "");
        // Remove port and path
        int colonIndex = withoutProtocol.indexOf(':');
        int slashIndex = withoutProtocol.indexOf('/');
        
        if (colonIndex > 0) {
            return withoutProtocol.substring(0, colonIndex);
        } else if (slashIndex > 0) {
            return withoutProtocol.substring(0, slashIndex);
        } else {
            return withoutProtocol;
        }
    }

    private int extractPort(String url) {
        if (url.startsWith("https://")) {
            return 443;
        } else if (url.startsWith("http://")) {
            return 80;
        }
        return 443; // default
    }
}
```

## Step 3: Update HttpServerVerticle Wiring

```java
private void initializeServices() {
    // ... existing code ...
    
    // HTTP adapter for fetching calculation rules
    JsonObject calcRulesConfig = config().getJsonObject("calculation-rules");
    com.tvpc.adapter.out.http.CalculationRuleHttpAdapter ruleHttpAdapter = 
            new com.tvpc.adapter.out.http.CalculationRuleHttpAdapter(vertx, calcRulesConfig);
    
    ConfigurationRepository configurationRepository = 
            new InMemoryConfigurationAdapter(vertx, ruleHttpAdapter);
    
    // ... rest of the code ...
}
```

## Step 4: Expected API Contract

### Request
```http
GET /api/v1/calculation-rules HTTP/1.1
Host: external-system.company.com
X-API-Key: your-api-key-here
Accept: application/json
```

### Response (Success)
```json
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "pts": "PTS-A",
    "processingEntity": "PE-001",
    "includedBusinessStatuses": ["PENDING", "VERIFIED"],
    "includedDirections": ["PAY"],
    "includedSettlementTypes": ["GROSS", "NET"]
  },
  {
    "pts": "PTS-B",
    "processingEntity": "PE-002",
    "includedBusinessStatuses": ["VERIFIED"],
    "includedDirections": ["PAY"],
    "includedSettlementTypes": ["GROSS"]
  }
]
```

### Response (Error)
```json
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "error": "Database connection failed",
  "timestamp": "2026-01-05T12:34:56Z"
}
```

## Step 5: Testing

### 1. Unit Test with Mock
```java
@Test
void testFetchRules_success() {
    // Mock WebClient response
    JsonArray mockResponse = new JsonArray()
        .add(new JsonObject()
            .put("pts", "PTS-A")
            .put("processingEntity", "PE-001")
            .put("includedBusinessStatuses", new JsonArray().add("PENDING"))
            .put("includedDirections", new JsonArray().add("PAY"))
            .put("includedSettlementTypes", new JsonArray().add("GROSS")));
    
    // Test parsing logic
    // ...
}
```

### 2. Integration Test
```bash
# Test external system connectivity
curl -H "X-API-Key: your-api-key" \
     https://external-system.company.com/api/v1/calculation-rules

# Start application and check logs
mvn exec:java -Dexec.mainClass="com.tvpc.Main"

# Look for:
# "Successfully fetched N calculation rules"
```

### 3. Manual Verification
```bash
# Check cached rules via logs
grep "Cached rule" logs/application.log

# Verify periodic refresh
grep "Periodic refresh" logs/application.log
```

## Step 6: Error Handling

### Network Timeout
```
2026-01-05 12:34:56 ERROR c.t.a.o.h.CalculationRuleHttpAdapter - Error fetching calculation rules: Connection timeout
2026-01-05 12:34:56 ERROR c.t.a.o.p.InMemoryConfigurationAdapter - Failed to refresh rules: Connection timeout, keeping cached rules
```
**Action**: System continues with cached rules

### Invalid Response
```
2026-01-05 12:34:56 ERROR c.t.a.o.h.CalculationRuleHttpAdapter - Failed to parse rule at index 2: Invalid business status: UNKNOWN
```
**Action**: Skips invalid rule, continues parsing others

### Authentication Failure
```
2026-01-05 12:34:56 ERROR c.t.a.o.h.CalculationRuleHttpAdapter - Failed to fetch rules: HTTP 401 - Unauthorized
```
**Action**: Check API key configuration

## Step 7: Monitoring

### Key Metrics to Track
1. **Refresh Success Rate**: Should be > 99%
2. **Response Time**: Should be < 1 second
3. **Cache Age**: Time since last successful refresh
4. **Parse Errors**: Should be 0

### Alerting Rules
```yaml
alerts:
  - name: "Calculation Rules Refresh Failed"
    condition: "refresh_failures > 3 in 15 minutes"
    severity: "high"
    
  - name: "Calculation Rules Stale"
    condition: "cache_age > 30 minutes"
    severity: "medium"
    
  - name: "Calculation Rules Parse Errors"
    condition: "parse_errors > 0"
    severity: "low"
```

## Step 8: Rollback Plan

If integration fails, revert to dummy data:

1. Keep the old `CalculationRuleHttpAdapter` as `CalculationRuleHttpAdapterDummy`
2. Switch back in `HttpServerVerticle`:
```java
// Rollback to dummy data
com.tvpc.adapter.out.http.CalculationRuleHttpAdapter ruleHttpAdapter = 
        new com.tvpc.adapter.out.http.CalculationRuleHttpAdapterDummy();
```

## Step 9: Performance Tuning

### Connection Pooling
```java
WebClientOptions options = new WebClientOptions()
    .setMaxPoolSize(10)
    .setKeepAlive(true)
    .setKeepAliveTimeout(60);
```

### Retry Logic
```java
public Future<List<CalculationRule>> fetchRulesWithRetry(int maxAttempts) {
    return fetchRules()
        .recover(error -> {
            if (maxAttempts > 1) {
                log.warn("Retry fetching rules, attempts left: {}", maxAttempts - 1);
                return fetchRulesWithRetry(maxAttempts - 1);
            }
            return Future.failedFuture(error);
        });
}
```

## Step 10: Security Considerations

1. **HTTPS Only**: Always use HTTPS in production
2. **Certificate Validation**: Set `setTrustAll(false)` in production
3. **API Key Rotation**: Support key rotation without restart
4. **Secrets Management**: Use environment variables or secret manager
5. **Network Isolation**: Restrict outbound connections to external system only

## Checklist

- [ ] External system URL configured
- [ ] API key obtained and configured
- [ ] Network connectivity verified
- [ ] API contract validated
- [ ] Code updated with real HTTP call
- [ ] Unit tests updated
- [ ] Integration tests passed
- [ ] Error handling tested
- [ ] Monitoring configured
- [ ] Rollback plan documented
- [ ] Security review completed
- [ ] Performance tested under load

## Support

For issues during integration:
1. Check application logs for detailed error messages
2. Verify network connectivity: `curl -v https://external-system.company.com`
3. Test API endpoint directly with curl
4. Check firewall rules and proxy settings
5. Contact external system team for API issues
