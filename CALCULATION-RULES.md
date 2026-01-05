# Calculation Rules Feature

## Overview

The calculation rules feature allows dynamic configuration of which settlements should be included in running total calculations based on PTS (Payment Trading System) and Processing Entity combinations.

## Architecture

### Components

1. **CalculationRule** (Domain Model)
   - Location: `src/main/java/com/tvpc/domain/model/CalculationRule.java`
   - Contains: PTS, ProcessingEntity, and three filter sets:
     - `includedBusinessStatuses`: Which business statuses to include (e.g., PENDING, VERIFIED)
     - `includedDirections`: Which directions to include (e.g., PAY, RECEIVE)
     - `includedSettlementTypes`: Which settlement types to include (e.g., GROSS, NET)

2. **CalculationRuleHttpAdapter** (Output Adapter)
   - Location: `src/main/java/com/tvpc/adapter/out/http/CalculationRuleHttpAdapter.java`
   - Purpose: Fetches calculation rules from external system
   - Current Status: Returns dummy test data
   - TODO: Replace with actual HTTP call when external system is ready

3. **InMemoryConfigurationAdapter** (Configuration Repository)
   - Location: `src/main/java/com/tvpc/adapter/out/persistence/InMemoryConfigurationAdapter.java`
   - Features:
     - Caches rules in memory using ConcurrentHashMap
     - Automatic refresh every 5 minutes
     - Fallback to cached rules if refresh fails
     - Returns default rule if specific PTS+PE combination not found

4. **JdbcRunningTotalPersistenceAdapter** (Updated)
   - Location: `src/main/java/com/tvpc/adapter/out/persistence/JdbcRunningTotalPersistenceAdapter.java`
   - Enhancement: Dynamically builds SQL WHERE clause based on calculation rules
   - Applies filters for business status, direction, and settlement type

## How It Works

### 1. Initial Load
When the application starts:
- `InMemoryConfigurationAdapter` is created with Vertx and `CalculationRuleHttpAdapter`
- Initial rule fetch is triggered immediately
- Rules are cached in memory by PTS:ProcessingEntity key

### 2. Periodic Refresh
- Every 5 minutes, rules are automatically refreshed from external system
- If refresh fails, existing cached rules are retained
- Logs warning but continues operation with cached data

### 3. Rule Application
When calculating running totals:
1. `SettlementIngestionUseCaseImpl` fetches the rule for current PTS+PE
2. Converts rule enums to string sets
3. Passes filters to `JdbcRunningTotalPersistenceAdapter`
4. SQL dynamically builds WHERE clause with IN clauses for each filter
5. Only matching settlements are included in SUM calculation

## Example Rules (Dummy Data)

```java
// PTS-A / PE-001
- Business Statuses: PENDING, VERIFIED
- Directions: PAY
- Settlement Types: GROSS, NET

// PTS-B / PE-002
- Business Statuses: VERIFIED
- Directions: PAY
- Settlement Types: GROSS

// PTS-A / PE-002
- Business Statuses: PENDING, INVALID, VERIFIED
- Directions: PAY, RECEIVE
- Settlement Types: GROSS
```

## SQL Generation Example

For PTS-A / PE-001, the SQL WHERE clause becomes:

```sql
WHERE s.PTS = 'PTS-A'
  AND s.PROCESSING_ENTITY = 'PE-001'
  AND s.COUNTERPARTY_ID = 'CP-ABC'
  AND s.VALUE_DATE = '2025-12-31'
  AND s.ID <= 12345
  AND s.BUSINESS_STATUS IN ('PENDING', 'VERIFIED')
  AND s.DIRECTION IN ('PAY')
  AND s.SETTLEMENT_TYPE IN ('GROSS', 'NET')
  AND s.SETTLEMENT_VERSION = (...)
```

## Configuration

### Refresh Interval
Default: 5 minutes (300,000 ms)
Location: `InMemoryConfigurationAdapter.REFRESH_INTERVAL_MS`

### Default Rule
If no rule found for a PTS+PE combination:
- Business Statuses: PENDING, INVALID, VERIFIED
- Directions: PAY
- Settlement Types: GROSS, NET

## Testing

### Unit Tests
1. **CalculationRuleHttpAdapterTest**
   - Tests dummy data fetching
   - Validates rule structure
   - Location: `src/test/java/com/tvpc/adapter/out/http/CalculationRuleHttpAdapterTest.java`

2. **InMemoryConfigurationAdapterTest**
   - Tests rule caching
   - Tests refresh mechanism
   - Tests default rule fallback
   - Location: `src/test/java/com/tvpc/adapter/out/persistence/InMemoryConfigurationAdapterTest.java`

### Running Tests
```bash
mvn test "-Dtest=CalculationRuleHttpAdapterTest,InMemoryConfigurationAdapterTest"
```

## Future Enhancements

### 1. Replace Dummy Data with Real HTTP Call
Update `CalculationRuleHttpAdapter.fetchRules()`:
```java
public Future<List<CalculationRule>> fetchRules() {
    return webClient.get("/api/calculation-rules")
        .send()
        .map(response -> parseRules(response.bodyAsJsonArray()));
}
```

### 2. Add Rule Validation
- Validate that at least one status/direction/type is included
- Validate PTS and PE are not empty
- Log warnings for suspicious configurations

### 3. Add Metrics
- Track refresh success/failure rate
- Monitor cache hit rate
- Alert on repeated refresh failures

### 4. Add Manual Refresh Endpoint
```http
POST /api/admin/refresh-rules
```

## Logging

Key log messages:
- `INFO`: "Fetching calculation rules from external system (dummy data)"
- `INFO`: "Successfully refreshed N calculation rules"
- `WARN`: "No calculation rule found for pts=X, processingEntity=Y, using default"
- `ERROR`: "Failed to refresh rules: {error}"
- `DEBUG`: "Cached rule for pts=X, processingEntity=Y"
- `DEBUG`: "Applying calculation rule: statuses=..., directions=..., types=..."

## Performance Considerations

1. **In-Memory Cache**: O(1) lookup by PTS:PE key
2. **Concurrent Access**: Thread-safe ConcurrentHashMap
3. **SQL Performance**: IN clauses are efficient for small sets (typically 2-5 values)
4. **Refresh Impact**: Async refresh doesn't block request processing
5. **Fallback Strategy**: Never fails due to refresh errors

## Troubleshooting

### Rules Not Updating
- Check logs for "Failed to refresh rules" errors
- Verify external system is accessible
- Check application startup logs to verify initial rule load succeeded

### Unexpected Calculations
- Check logs for "Applying calculation rule" to see which filters are active
- Verify rule configuration in external system
- Test with specific PTS+PE combination using `getCalculationRule()`

### Default Rule Being Used
- Check logs for "No calculation rule found" warning
- Verify PTS and PE values match exactly (case-sensitive)
- Confirm rule exists in external system
