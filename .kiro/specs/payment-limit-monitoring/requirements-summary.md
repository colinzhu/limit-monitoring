# Requirements Summary

## Overview
Payment Limit Monitoring System tracks settlement flows, calculates exposure by counterparty/value date, and flags transactions exceeding limits for manual approval.

## Core Requirements

### 1. Settlement Ingestion & Validation
- **Input**: Settlements from PTS with PTS, Processing_Entity, Counterparty_ID, Value_Date, Currency, Amount, Settlement_ID, Settlement_Version, Settlement_Direction (PAY/RECEIVE), Settlement_Type (NET/GROSS), Business_Status (PENDING/INVALID/VERIFIED/CANCELLED)
- **Validation**: Reject invalid data (missing fields, bad currency codes, negative amounts, invalid dates)
- **Versioning**: Maintain latest version, preserve history for audit
- **Filtering**: Apply rules from external rule system (fetched every 5 minutes)
- **Inclusion Logic**: Include in calculations if direction=PAY, business_status≠CANCELLED, and matches filtering rules
- **Output**: Store settlement immutably

### 2. Group Aggregation
- **Grouping Key**: PTS + Processing_Entity + Counterparty_ID + Value_Date
- **Calculation**: Sum all included PAY settlements (business_status not CANCELLED)
- **Recalculation Strategy**: Complete recalculation on every change (not incremental)
- **Trigger Events**: New settlement, version update, direction change, rule change, group key change
- **Concurrency**: Atomic operations to prevent race conditions

### 3. Status Management
- **CREATED**: Group subtotal ≤ limit (default for RECEIVE/CANCELLED)
- **BLOCKED**: Group subtotal > limit (PAY settlements only)
- **PENDING_AUTHORISE**: Requested release, awaiting authorization
- **AUTHORISED**: Both request and authorize completed
- **Dynamic Computation**: Status computed on-demand, no stored status fields

### 4. Approval Workflow
- **Two-Step Process**:
  1. REQUEST RELEASE → PENDING_AUTHORISE (by user A)
  2. AUTHORISE → AUTHORISED (by user B, different from A)
- **Eligibility**: Only VERIFIED PAY settlements that are BLOCKED
- **Segregation**: Same user cannot both request and authorize
- **Bulk Actions**: Allowed for same-group VERIFIED settlements only
- **Audit Trail**: Complete log of all actions with timestamps and user IDs

### 5. Performance Targets
- **Volume**: 200,000 settlements in 30 minutes (peak)
- **Status Availability**: Within 30 seconds of ingestion
- **Subtotal Recalculation**: Within 10 seconds
- **API Response**: Within 3 seconds p99

### 6. Search & Filtering
- **Filters**: PTS, Processing_Entity, Value_Date, Counterparty_ID, Direction, Type, Business_Status
- **View Options**:
  - Only blocked PAY settlements
  - Only non-blocked PAY settlements
  - All settlements
- **Display**: Groups in upper section, individual settlements in lower section
- **Export**: Excel file with all details

### 7. External API
- **Query by Settlement_ID**: Returns current status and details
- **Manual Recalculation**: API endpoint for scope-based recalculation (requires admin auth)
- **Notifications**: Sent to external systems when status becomes AUTHORISED
- **Retry**: Exponential backoff for 24 hours if external system unavailable

### 8. Configuration
- **Exposure Limit**: 500M USD (MVP) or counterparty-specific (advanced)
- **Exchange Rates**: Fetched daily, used at processing time
- **Rule System**: External system providing filtering criteria
- **Updates**: Re-evaluate affected groups on limit/rule changes

### 9. Distributed Processing
- **Consistency**: Multiple instances must maintain data consistency
- **Version Ordering**: Apply only latest version based on Settlement_Version
- **Idempotency**: Handle duplicate submissions
- **Fault Tolerance**: Survive restarts without data loss

### 10. Audit & Compliance
- **Data Retention**: 7 years
- **Historical Access**: All settlement versions and approval actions
- **Reports**: Settlement data, subtotals, review status in standard formats
- **Integrity**: Prevent unauthorized modifications to historical records

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| No stored status fields | Avoid mass updates, compute on-demand |
| No stored USD equivalents | Use latest rates at query time |
| Complete recalculation | Ensures data consistency |
| Single-threaded processor | Eliminates race conditions |
| Immutable settlements | Audit trail, version history |
| Async processing | Meet performance targets |

## Implementation Clarifications

1. **Performance vs Consistency**: Use materialized views, async background processing, event sourcing
2. **Validation Rules**: All required fields, ISO 4217 currency, positive amounts, future dates
3. **Approval Security**: Track by user ID, check audit trail for requester≠authorizer
4. **Exchange Rates**: Fixed at processing time, don't affect historical calculations
5. **NET Settlements**: Direction changes come as new versions, trigger group moves
6. **Manual Recalc**: Requires admin/supervisor privileges, logged with scope
7. **Notifications**: 24-hour retry with exponential backoff (1min, 2min, 4min...)
8. **Rule Updates**: Re-evaluate affected groups when new rules fetched
9. **Data Archiving**: After 7 years to cold storage, restorable within 24-48 hours
10. **UI Indicators**: Visual distinction for settlement types and business statuses

## Glossary (Key Terms)

- **PAY Settlement**: Outgoing payment, contributes to exposure
- **RECEIVE Settlement**: Incoming payment, excluded from exposure
- **NET Settlement**: Netted from multiple settlements, direction can change
- **GROSS Settlement**: Individual settlement, fixed direction
- **Subtotal**: Aggregated USD amount for a group
- **Exposure Limit**: Maximum allowed subtotal (500M or counterparty-specific)
- **Group**: Settlements grouped by PTS, Processing_Entity, Counterparty, Value_Date
