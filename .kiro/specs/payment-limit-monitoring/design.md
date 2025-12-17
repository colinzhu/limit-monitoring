# Payment Limit Monitoring System - Design Document

## Overview

The Payment Limit Monitoring System is a high-performance financial risk management application designed to process up to 200,000 settlements within 30 minutes during peak trading periods. The system aggregates settlement data by counterparty groups, applies configurable filtering rules, and enforces exposure limits through a two-step approval workflow.

The architecture emphasizes real-time processing, data consistency, and operational transparency while maintaining audit trails for regulatory compliance. The system integrates with multiple external systems including Primary Trading Systems (PTS), rule engines, exchange rate providers, and limit management systems.

## Architecture

The system follows a microservices architecture with event-driven processing to handle high-volume settlement flows efficiently:

### Core Components
- **Settlement Ingestion Service**: Receives and validates settlement flows from multiple PTS endpoints
- **Rule Engine Integration**: Fetches and applies filtering criteria from external rule systems
- **Aggregation Engine**: Calculates group subtotals with currency conversion
- **Limit Monitoring Service**: Evaluates groups against exposure limits and manages status transitions
- **Approval Workflow Service**: Handles two-step approval process with audit trails
- **API Gateway**: Provides external system integration and manual recalculation endpoints
- **Notification Service**: Sends authorization notifications to downstream systems
- **Data Access Layer**: Manages settlement storage, versioning, and historical data

### External Integrations
- **Primary Trading Systems (PTS)**: Source of settlement flows
- **Rule Management System**: Provides filtering criteria (fetched every 5 minutes)
- **Exchange Rate Provider**: Daily currency conversion rates
- **Limit Management System**: Counterparty-specific exposure limits (future enhancement)
- **Downstream Processing Systems**: Receive authorization notifications

### Technology Stack
- **Message Queue**: Apache Kafka for high-throughput settlement ingestion
- **Database**: PostgreSQL with read replicas for scalability
- **Caching**: Redis for frequently accessed data (exchange rates, rules, limits)
- **API Framework**: REST APIs with OpenAPI specification
- **Background Processing**: Scheduled jobs for rule and rate synchronization

## Components and Interfaces

### Settlement Ingestion Service
**Responsibilities:**
- Receive settlement flows from PTS endpoints
- Validate settlement data structure and completeness
- Apply filtering rules to determine calculation eligibility
- Store settlements with versioning support
- Trigger aggregation calculations

**Key Interfaces:**
```
POST /api/settlements/ingest
- Accepts settlement data from PTS systems
- Returns acknowledgment with processing status

GET /api/settlements/{settlementId}/status
- Returns current settlement status and details
- Used by external systems for status queries
```

### Aggregation Engine
**Responsibilities:**
- Group settlements by PTS, Processing Entity, Counterparty ID, and Value Date
- Convert currencies to USD using latest exchange rates
- Calculate and maintain group subtotals
- Handle settlement version updates and group migrations
- Trigger limit evaluations

**Key Operations:**
- Real-time subtotal calculation (< 10 seconds)
- Group rebalancing when settlement details change
- Currency conversion with point-in-time rates
- Efficient handling of high-volume updates

### Limit Monitoring Service
**Responsibilities:**
- Compare group subtotals against exposure limits
- Manage settlement status transitions (CREATED → BLOCKED → PENDING_AUTHORISE → AUTHORISED)
- Handle limit updates and re-evaluation
- Support both fixed limits (MVP) and counterparty-specific limits

**Status Management:**
- CREATED: Group subtotal within limit
- BLOCKED: Group subtotal exceeds limit
- PENDING_AUTHORISE: After REQUEST RELEASE action
- AUTHORISED: After AUTHORISE action by different user

### Approval Workflow Service
**Responsibilities:**
- Enforce two-person approval process
- Prevent same user from performing both REQUEST RELEASE and AUTHORISE
- Maintain comprehensive audit trails
- Handle bulk operations on same-group settlements
- Reset approvals when settlement versions change

**Audit Trail Fields:**
- User identity and timestamp
- Action type (REQUEST RELEASE, AUTHORISE)
- Settlement ID and version
- Group context and subtotal at time of action

## Data Models

### Settlement Entity
```typescript
interface Settlement {
  settlementId: string;
  settlementVersion: number;
  pts: string;
  processingEntity: string;
  counterpartyId: string;
  valueDate: Date;
  currency: string;
  amount: number;
  status: SettlementStatus;
  isEligibleForCalculation: boolean;
  usdEquivalent?: number;
  exchangeRateUsed?: number;
  createdAt: Date;
  updatedAt: Date;
}

enum SettlementStatus {
  CREATED = 'CREATED',
  BLOCKED = 'BLOCKED',
  PENDING_AUTHORISE = 'PENDING_AUTHORISE',
  AUTHORISED = 'AUTHORISED'
}
```

### Settlement Group
```typescript
interface SettlementGroup {
  groupId: string;
  pts: string;
  processingEntity: string;
  counterpartyId: string;
  valueDate: Date;
  subtotalUsd: number;
  exposureLimit: number;
  settlementCount: number;
  exceedsLimit: boolean;
  lastCalculatedAt: Date;
}
```

### Audit Record
```typescript
interface AuditRecord {
  auditId: string;
  settlementId: string;
  settlementVersion: number;
  userId: string;
  action: AuditAction;
  timestamp: Date;
  groupContext: {
    pts: string;
    processingEntity: string;
    counterpartyId: string;
    valueDate: Date;
    subtotalAtAction: number;
  };
}

enum AuditAction {
  REQUEST_RELEASE = 'REQUEST_RELEASE',
  AUTHORISE = 'AUTHORISE',
  STATUS_RESET = 'STATUS_RESET'
}
```

### Exchange Rate
```typescript
interface ExchangeRate {
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  effectiveDate: Date;
  fetchedAt: Date;
}
```

### Filtering Rule
```typescript
interface FilteringRule {
  ruleId: string;
  criteria: {
    pts?: string[];
    processingEntity?: string[];
    counterpartyId?: string[];
    currency?: string[];
    amountRange?: {
      min?: number;
      max?: number;
    };
  };
  isActive: boolean;
  lastUpdated: Date;
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system-essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

Based on the requirements analysis, the following correctness properties must be maintained by the Payment Limit Monitoring System:

### Data Management Properties

**Property 1: Settlement Storage Completeness**
*For any* valid settlement data received, the system should store all required fields (PTS, Processing_Entity, Counterparty_ID, Value_Date, Currency, Amount, Settlement_ID, Settlement_Version) without loss or corruption
**Validates: Requirements 1.1**

**Property 2: Filtering Rule Application**
*For any* settlement and current filtering rules, the system should correctly determine eligibility for subtotal calculations based on the rule criteria
**Validates: Requirements 1.2**

**Property 3: Version Management**
*For any* settlement with multiple versions, only the latest version should be active in calculations while all historical versions remain accessible for audit
**Validates: Requirements 1.5**

**Property 4: Data Preservation**
*For any* settlement stored, the original currency and amount values should remain unchanged after storage operations
**Validates: Requirements 1.6**

**Property 5: Invalid Data Rejection**
*For any* settlement with invalid or incomplete data, the system should reject it and not include it in any calculations
**Validates: Requirements 1.7**

### Aggregation and Calculation Properties

**Property 6: Settlement Grouping**
*For any* set of settlements, those with identical PTS, Processing_Entity, Counterparty_ID, and Value_Date should be grouped together
**Validates: Requirements 2.1**

**Property 7: Subtotal Calculation Accuracy**
*For any* group of settlements, the subtotal should equal the sum of USD-converted amounts for all eligible settlements in the group
**Validates: Requirements 2.2**

**Property 8: Incremental Subtotal Updates**
*For any* existing group, adding a new eligible settlement should increase the subtotal by exactly the USD equivalent of the new settlement's amount
**Validates: Requirements 2.3**

**Property 9: Version Update Consistency**
*For any* settlement version update, the group subtotal should reflect only the new version's amount and exclude the previous version's contribution
**Validates: Requirements 2.4**

**Property 10: Group Migration Accuracy**
*For any* settlement that changes group keys (PTS, Processing_Entity, Counterparty_ID, or Value_Date), it should be removed from the old group and added to the correct new group, with both subtotals recalculated accurately
**Validates: Requirements 2.5**

### Status Management Properties

**Property 11: Status Assignment for Compliant Groups**
*For any* settlement in a group where the subtotal is within the exposure limit, the settlement status should be CREATED
**Validates: Requirements 3.1**

**Property 12: Status Assignment for Exceeding Groups**
*For any* settlement in a group where the subtotal exceeds the exposure limit, the settlement status should be BLOCKED
**Validates: Requirements 3.2**

**Property 13: Limit Change Re-evaluation**
*For any* exposure limit update, all settlements should be re-evaluated and their statuses should reflect the new limit comparison
**Validates: Requirements 3.3**

**Property 14: Status Reset on Version Change**
*For any* settlement that receives a new version after approval actions, the status should reset to CREATED or BLOCKED based on the current group subtotal, invalidating previous approvals
**Validates: Requirements 2.6, 4.5**

### Approval Workflow Properties

**Property 15: REQUEST RELEASE Transition**
*For any* BLOCKED settlement, a REQUEST RELEASE action should change the status to PENDING_AUTHORISE and create an audit record
**Validates: Requirements 4.2**

**Property 16: AUTHORISE Transition**
*For any* PENDING_AUTHORISE settlement, an AUTHORISE action by a different user should change the status to AUTHORISED and create an audit record
**Validates: Requirements 4.3**

**Property 17: Segregation of Duties**
*For any* settlement, the same user should not be able to perform both REQUEST RELEASE and AUTHORISE actions
**Validates: Requirements 4.4**

**Property 18: Bulk Action Consistency**
*For any* bulk operation on settlements from the same group, each settlement should receive the action and have an individual audit entry created
**Validates: Requirements 4.7**

**Property 19: Audit Trail Completeness**
*For any* user action (REQUEST RELEASE, AUTHORISE), a complete audit record should be created with user identity, timestamp, settlement details, and version information
**Validates: Requirements 4.8**

### Search and Query Properties

**Property 20: Search Filter Accuracy**
*For any* search criteria (PTS, Processing_Entity, Value_Date, Counterparty_ID), the results should contain only settlements that match all specified criteria
**Validates: Requirements 6.1, 6.3**

**Property 21: Limit Status Filtering**
*For any* limit status filter (exceeds/does not exceed), the results should contain only settlements whose groups match the specified limit relationship
**Validates: Requirements 6.2**

**Property 22: Group Selection Display**
*For any* selected settlement group, the detail view should display all and only the settlements that belong to that specific group
**Validates: Requirements 6.8**

### API and Integration Properties

**Property 23: Status Query Accuracy**
*For any* valid Settlement_ID queried via API, the system should return the current accurate status and relevant details
**Validates: Requirements 7.1**

**Property 24: Authorization Notification**
*For any* settlement that transitions to AUTHORISED status, a notification should be sent to external systems with the Settlement_ID and authorization details
**Validates: Requirements 7.6**

**Property 25: Manual Recalculation Scope**
*For any* manual recalculation request with scope criteria, all and only the settlements matching the criteria should have their subtotals recalculated using current rules and limits
**Validates: Requirements 7.7, 7.8**

### Configuration and Rate Management Properties

**Property 26: Exchange Rate Application**
*For any* settlement requiring currency conversion, the latest available exchange rate at processing time should be used for USD equivalent calculation
**Validates: Requirements 8.4**

**Property 27: Rate Update Non-Retroactivity**
*For any* exchange rate update, existing settlement subtotals should remain unchanged and only future settlements should use the new rates
**Validates: Requirements 8.5**

**Property 28: Counterparty Limit Application**
*For any* settlement in advanced mode, the correct counterparty-specific exposure limit should be applied based on the settlement's Counterparty_ID
**Validates: Requirements 8.2**

### Audit and Compliance Properties

**Property 29: Historical Data Accessibility**
*For any* settlement, all versions and their timestamps should remain accessible for audit and compliance queries
**Validates: Requirements 9.1**

**Property 30: Audit Trail Immutability**
*For any* historical audit record, it should remain unmodifiable and accessible throughout the compliance retention period
**Validates: Requirements 9.5**

## Error Handling

The system implements comprehensive error handling across all components:

### Settlement Ingestion Errors
- **Invalid Data Format**: Reject settlements with missing required fields or invalid data types
- **Duplicate Processing**: Handle duplicate settlement IDs with proper versioning
- **External System Failures**: Implement retry mechanisms with exponential backoff for PTS connectivity issues
- **Rate Limiting**: Protect against overwhelming settlement volumes with circuit breaker patterns

### Calculation Errors
- **Currency Conversion Failures**: Handle missing exchange rates with fallback mechanisms and error notifications
- **Arithmetic Overflow**: Prevent calculation errors with proper numeric handling for large amounts
- **Group Migration Errors**: Ensure atomic operations when moving settlements between groups
- **Concurrent Update Conflicts**: Use optimistic locking to handle simultaneous updates to the same group

### Approval Workflow Errors
- **Authorization Failures**: Validate user permissions before allowing approval actions
- **Stale Data Operations**: Prevent actions on outdated settlement versions
- **Audit Trail Failures**: Ensure audit records are created atomically with status changes
- **Notification Failures**: Implement retry mechanisms for external system notifications

### Integration Errors
- **Rule System Unavailability**: Continue operations with cached rules when external rule system is unavailable
- **API Rate Limiting**: Implement proper throttling for external API calls
- **Data Consistency**: Ensure eventual consistency across distributed components
- **Timeout Handling**: Implement appropriate timeouts for all external system calls

## Testing Strategy

The Payment Limit Monitoring System requires a comprehensive testing approach combining unit tests and property-based tests to ensure correctness across the complex financial workflows.

### Unit Testing Approach
Unit tests will focus on:
- **Component Integration**: Testing interactions between services and data layers
- **Edge Cases**: Specific scenarios like boundary conditions, error states, and configuration changes
- **Business Logic**: Validation of specific approval workflows and status transitions
- **API Contracts**: Ensuring API responses match expected formats and contain required data

### Property-Based Testing Approach
Property-based tests will verify universal properties using **fast-check** (JavaScript/TypeScript property testing library) with a minimum of 100 iterations per test to ensure comprehensive coverage across the input space.

Each property-based test will:
- Generate random but valid test data (settlements, rules, limits, user actions)
- Execute system operations with the generated data
- Verify that the correctness properties hold regardless of the specific input values
- Include explicit comments referencing the design document properties being tested

**Property Test Requirements:**
- All correctness properties (Property 1-30) must be implemented as individual property-based tests
- Each test must be tagged with the format: `**Feature: payment-limit-monitoring, Property X: [property description]**`
- Tests must use realistic data generators that respect business constraints (valid currencies, reasonable amounts, proper date ranges)
- Complex properties involving multiple operations (like group migration) must test the complete workflow atomically

### Test Data Generation Strategy
- **Settlement Generators**: Create settlements with valid PTS, processing entities, counterparties, currencies, and amounts
- **Rule Generators**: Generate filtering rules with various criteria combinations
- **User Action Generators**: Create approval workflows with proper user segregation
- **Temporal Generators**: Generate realistic value dates and processing timestamps
- **Currency Generators**: Use actual currency codes and reasonable exchange rate ranges

### Integration Testing
- **End-to-End Workflows**: Test complete settlement processing from ingestion to authorization
- **External System Mocking**: Mock PTS, rule systems, and notification endpoints for controlled testing
- **Performance Testing**: Validate system behavior under high-volume settlement loads
- **Failure Recovery**: Test system resilience during external system outages

The dual testing approach ensures both specific business scenarios work correctly (unit tests) and that the system maintains correctness properties across all possible inputs (property-based tests), providing confidence in the system's reliability for high-stakes financial operations.