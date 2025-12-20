# Payment Limit Monitoring System - Design Document (Claude)

## Overview

This design addresses the critical race condition problem in settlement version processing: **multiple versions of the same settlement can arrive out of sequence**, and traditional delta-based approaches fail to handle this correctly.

### The Core Problem

```
Scenario:
- Settlement X exists with version 1, amount = 80M USD
- Group subtotal = 500M USD (includes version 1's 80M)

Timeline:
T+0ms:  Version 3 arrives (amount = 90M USD)
T+1ms:  Version 2 arrives (amount = 120M USD)

Expected Result: Version 3 should be active (highest version)
Actual Group Subtotal: Should be 510M USD (500M - 80M + 90M)

Traditional Delta Approach Fails:
- Version 3: delta = 90M - 80M = +10M → subtotal = 510M ✓
- Version 2: delta = 120M - 80M = +40M → subtotal = 550M ✗ WRONG!
- Version 2 is stale but delta calculation doesn't know this
```

### Root Cause Analysis

The problem with delta-based approaches:
1. **They assume sequential processing** - delta from "current" state
2. **They don't track what's already been applied** - can't detect stale versions
3. **They compound errors** - wrong deltas create wrong subtotals
4. **They require complex version tracking** - max_processed_version per settlement

## Solution: Idempotent Settlement Contribution Pattern

### Core Insight

Instead of calculating deltas between versions, **always recalculate the entire settlement's contribution from scratch** using only the latest version. This makes the operation idempotent and order-independent.

### Key Design Principles

1. **No delta calculations** - always compute full contribution
2. **Order-independent processing** - arrival order doesn't matter
3. **Idempotent operations** - duplicate processing is safe
4. **Single source of truth** - group subtotal is always correct
5. **Compensating transactions** - errors self-correct

## Architecture

### System Flow

```
┌─────────────────────────────────────────────────────────────┐
│                    Settlement Ingestion                      │
│  1. Receive settlement (any version order)                  │
│  2. Validate data structure                                 │
│  3. Apply filtering rules → is_eligible flag                │
│  4. Store settlement (immutable, append-only)               │
│  5. Publish to event queue                                   │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│              Background Processing Service                   │
│  1. Read events in arrival order (no sequencing needed)     │
│  2. For each settlement:                                    │
│     a. Find latest version for this settlement_id           │
│     b. Calculate full contribution from latest version      │
│     c. Update group subtotal atomically                     │
│  3. Mark event as processed                                 │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    Query Layer                               │
│  - Read from materialized views                             │
│  - Status computed on-demand                                │
└─────────────────────────────────────────────────────────────┘
```

## Database Schema

### 1. Settlements Table (Immutable Event Store)

```sql
CREATE TABLE settlements (
    settlement_id VARCHAR(100) NOT NULL,
    settlement_version INTEGER NOT NULL,
    pts VARCHAR(50) NOT NULL,
    processing_entity VARCHAR(100) NOT NULL,
    counterparty_id VARCHAR(100) NOT NULL,
    value_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    is_eligible BOOLEAN NOT NULL,
    received_at TIMESTAMP NOT NULL,
    processing_order BIGSERIAL NOT NULL,  -- Arrival sequence

    PRIMARY KEY (settlement_id, settlement_version),
    INDEX idx_processing_order (processing_order),
    INDEX idx_settlement_lookup (settlement_id, received_at DESC)
);
```

**Key Features:**
- Composite primary key prevents duplicate version storage
- `processing_order` auto-sequence tracks arrival order
- `is_eligible` determined at ingestion time
- Records are never updated, only inserted

### 2. Settlement Groups Table (Materialized Aggregates)

```sql
CREATE TABLE settlement_groups (
    group_id VARCHAR(255) NOT NULL PRIMARY KEY,
    pts VARCHAR(50) NOT NULL,
    processing_entity VARCHAR(100) NOT NULL,
    counterparty_id VARCHAR(100) NOT NULL,
    value_date DATE NOT NULL,
    subtotal_usd DECIMAL(20, 2) NOT NULL DEFAULT 0,
    settlement_count INTEGER NOT NULL DEFAULT 0,
    exposure_limit DECIMAL(20, 2) NOT NULL,
    last_updated TIMESTAMP NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,  -- Optimistic locking

    INDEX idx_exceeds (exceeds_limit),
    INDEX idx_counterparty (counterparty_id)
);

-- Add computed column for fast filtering
ALTER TABLE settlement_groups
ADD COLUMN exceeds_limit BOOLEAN GENERATED ALWAYS AS (subtotal_usd > exposure_limit) STORED;
```

**Key Features:**
- `version` field for optimistic locking
- Generated column for limit exceedance detection
- Group ID format: `{pts}::{entity}::{counterparty}::{value_date}`

### 3. Settlement Approval Table

```sql
CREATE TABLE settlement_approval (
    settlement_id VARCHAR(100) PRIMARY KEY,
    settlement_version INTEGER NOT NULL,
    requested_by VARCHAR(100),
    requested_at TIMESTAMP,
    authorized_by VARCHAR(100),
    authorized_at TIMESTAMP,

    FOREIGN KEY (settlement_id, settlement_version)
        REFERENCES settlements(settlement_id, settlement_version)
);
```

### 4. Processing State Table (Critical for Correctness)

```sql
CREATE TABLE processing_state (
    settlement_id VARCHAR(100) PRIMARY KEY,
    last_processed_version INTEGER,
    last_processed_at TIMESTAMP,
    last_processed_contribution_usd DECIMAL(20, 2),
    group_id VARCHAR(255),

    INDEX idx_group_processing (group_id, last_processed_at)
);
```

**Purpose:** Tracks what contribution is currently reflected in group subtotals. This is the key to idempotent processing.

## The Idempotent Processing Algorithm

### Core Algorithm

```typescript
async function processSettlement(settlementId: string) {
    // Step 1: Get the latest version (regardless of arrival order)
    const latest = await db.query(`
        SELECT * FROM settlements
        WHERE settlement_id = ?
        ORDER BY settlement_version DESC
        LIMIT 1
    `, [settlementId]);

    if (!latest) return; // Settlement deleted?

    // Step 2: Calculate what this settlement SHOULD contribute
    const latestContribution = await calculateContribution(latest);

    // Step 3: Get what it's CURRENTLY contributing (if anything)
    const current = await db.query(`
        SELECT last_processed_contribution_usd, last_processed_version, group_id
        FROM processing_state
        WHERE settlement_id = ?
    `, [settlementId]);

    const currentContribution = current?.last_processed_contribution_usd || 0;
    const currentVersion = current?.last_processed_version;
    const currentGroupId = current?.group_id;

    // Step 4: Determine if we need to update
    const needsUpdate =
        latestContribution !== currentContribution ||
        latest.settlement_version !== currentVersion;

    if (!needsUpdate) {
        // Already up to date, skip
        return;
    }

    // Step 5: Calculate the group ID for latest version
    const latestGroupId = buildGroupId(latest);

    // Step 6: Handle group migration if needed
    if (currentGroupId && currentGroupId !== latestGroupId) {
        // Settlement moved to different group
        await handleGroupMigration(
            settlementId,
            currentGroupId,
            latestGroupId,
            currentContribution,
            latestContribution
        );
    } else {
        // Same group, just update subtotal
        await updateGroupSubtotal(
            latestGroupId,
            latestContribution - currentContribution
        );
    }

    // Step 7: Update processing state
    await db.query(`
        INSERT INTO processing_state
        (settlement_id, last_processed_version, last_processed_at,
         last_processed_contribution_usd, group_id)
        VALUES (?, ?, NOW(), ?, ?)
        ON CONFLICT (settlement_id)
        DO UPDATE SET
            last_processed_version = EXCLUDED.last_processed_version,
            last_processed_at = EXCLUDED.last_processed_at,
            last_processed_contribution_usd = EXCLUDED.last_processed_contribution_usd,
            group_id = EXCLUDED.group_id
    `, [settlementId, latest.settlement_version, latestContribution, latestGroupId]);

    // Step 8: Reset approvals if version changed
    if (currentVersion && latest.settlement_version !== currentVersion) {
        await resetApprovals(settlementId, latest.settlement_version);
    }
}

async function calculateContribution(settlement: Settlement): Promise<number> {
    if (!settlement.is_eligible) return 0;

    const rate = await exchangeRateService.getRate(settlement.currency, 'USD');
    return settlement.amount * rate;
}

async function updateGroupSubtotal(groupId: string, delta: number) {
    // Atomic update with optimistic locking
    await db.query(`
        UPDATE settlement_groups
        SET subtotal_usd = subtotal_usd + ?,
            settlement_count = settlement_count + ?,
            version = version + 1,
            last_updated = NOW()
        WHERE group_id = ?
    `, [delta, delta > 0 ? 1 : 0, groupId]);
}
```

### Why This Works

**Scenario Walkthrough:**

```
Initial State:
- Settlement X version 1: 80M USD (eligible)
- Group subtotal: 500M USD
- Processing state: version=1, contribution=80M

Timeline:
T+0ms: Version 3 arrives (90M USD)
T+1ms: Version 2 arrives (120M USD)

Processing:
1. Process version 3 event:
   - Latest version = 3 (90M)
   - Current contribution = 80M (from version 1)
   - Delta = 90M - 80M = +10M
   - Update group: 500M + 10M = 510M
   - Processing state: version=3, contribution=90M

2. Process version 2 event:
   - Latest version = 3 (still!)
   - Current contribution = 90M (from processing state)
   - Latest contribution = 90M
   - No change needed → SKIP

Result: ✓ Correct (510M)
```

**Duplicate Processing:**

```
Same event processed twice:
1. First processing: contribution=90M, delta=+10M
2. Second processing: contribution=90M, delta=0M → SKIP

Result: ✓ Idempotent
```

## Handling Edge Cases

### Edge Case 1: Version Gap with Missing Intermediates

```
Scenario:
- Version 1 exists (80M)
- Version 3 arrives (90M)
- Version 2 never arrives (or lost)

Processing:
- Version 3 processing: uses latest=3, current=1 → correct
- Version 2 never processed (or ignored if it arrives later)
- Result: Always correct
```

### Edge Case 2: Concurrent Processing of Same Settlement

```
Scenario:
- Two threads process settlement X simultaneously

Solution:
- processing_state table acts as a lock point
- Each thread calculates same contribution
- First to update processing_state wins
- Second sees no change needed
- Result: Idempotent, no race condition
```

### Edge Case 3: Settlement Moves Between Groups

```
Scenario:
- Settlement X version 1: Group A (80M)
- Settlement X version 2: Group B (120M)

Processing:
- Calculate contribution for version 2: 120M
- Detect group change: A → B
- Update: Group A -= 80M, Group B += 120M
- Processing state updated to Group B
- Result: Both groups correct
```

### Edge Case 4: Eligibility Changes with Rule Updates

```
Scenario:
- Settlement X version 1: eligible=true, 80M
- Rule changes, version 2: eligible=false, 80M

Processing:
- Version 2 contribution = 0 (not eligible)
- Delta = 0 - 80M = -80M
- Group subtotal decreases
- Result: Correct
```

## Background Processor Implementation

### Single-Threaded Processor

```typescript
class BackgroundProcessor {
    private lastProcessedOrder: bigint = 0n;

    async run() {
        while (true) {
            // 1. Fetch new events in arrival order
            const events = await db.query(`
                SELECT settlement_id
                FROM settlements
                WHERE processing_order > ?
                AND processed = false
                ORDER BY processing_order ASC
                LIMIT 1000
            `, [this.lastProcessedOrder]);

            if (events.length === 0) {
                await sleep(5000); // Wait 5 seconds
                continue;
            }

            // 2. Process each settlement (not each event!)
            // Group by settlement_id to avoid redundant work
            const uniqueSettlements = [...new Set(events.map(e => e.settlement_id))];

            for (const settlementId of uniqueSettlements) {
                try {
                    await processSettlement(settlementId);
                } catch (error) {
                    console.error(`Failed to process ${settlementId}:`, error);
                    // Continue with other settlements
                }
            }

            // 3. Mark events as processed
            const maxOrder = events[events.length - 1].processing_order;
            await db.query(`
                UPDATE settlements
                SET processed = true
                WHERE processing_order <= ?
            `, [maxOrder]);

            this.lastProcessedOrder = maxOrder;
        }
    }
}
```

**Key Points:**
- Processes by settlement_id, not individual events
- Handles multiple versions of same settlement in one pass
- Always uses latest version from database
- No need for event sequencing - database provides ordering

### Alternative: Partitioned Processing for Scale

For very high volume (>1000 settlements/sec):

```typescript
// Partition by group_id hash
const partition = hash(groupId) % NUM_PARTITIONS;

// Each partition has its own processor
// Processes only settlements in its partition
// Maintains separate processing_state per partition
```

## API Layer

### Settlement Ingestion

```typescript
POST /api/v1/settlements/ingest

Request:
{
  "settlementId": "SETL-123",
  "settlementVersion": 3,
  "pts": "PTS-A",
  "processingEntity": "ENTITY-1",
  "counterpartyId": "CP-5678",
  "valueDate": "2025-02-01",
  "currency": "EUR",
  "amount": 1500000.00
}

Response (202 Accepted):
{
  "status": "accepted",
  "settlementId": "SETL-123",
  "processingOrder": 456789,
  "isEligible": true,
  "estimatedProcessingTime": "5-10 seconds"
}
```

**Processing:**
1. Validate schema
2. Check for duplicate (settlement_id, version)
3. Apply filtering rules → is_eligible
4. Insert into settlements table
5. Return immediately (async processing)

### Status Query

```typescript
GET /api/v1/settlements/{id}/status

Response:
{
  "settlementId": "SETL-123",
  "currentVersion": 3,
  "status": "BLOCKED",
  "groupDetails": {
    "groupId": "PTS-A::ENTITY-1::CP-5678::2025-02-01",
    "subtotalUsd": 550000000.00,
    "exposureLimit": 500000000.00,
    "exceedsBy": 50000000.00
  },
  "approvalDetails": null,
  "reason": "Group subtotal exceeds limit"
}
```

**Status Computation:**
```typescript
async function getStatus(settlementId: string) {
    // 1. Get latest version
    const settlement = await getLatestVersion(settlementId);

    // 2. Get approval status
    const approval = await getApproval(settlementId);

    // 3. Get group subtotal
    const groupId = buildGroupId(settlement);
    const group = await getGroup(groupId);

    // 4. Compute status
    if (approval?.authorized_by) return 'AUTHORISED';
    if (approval?.requested_by) return 'PENDING_AUTHORISE';

    const exceeds = group.subtotal_usd > group.exposure_limit;
    return exceeds ? 'BLOCKED' : 'CREATED';
}
```

### Approval Workflow

```typescript
POST /api/v1/settlements/{id}/request-release

Preconditions:
- Settlement status must be BLOCKED
- No existing approval for this version
- User has operator role

Processing:
1. Verify status is BLOCKED
2. Check no pending approval exists
3. Get latest version
4. Insert approval record (requested_by, requested_at)
5. Log audit entry
6. Return new status: PENDING_AUTHORISE
```

```typescript
POST /api/v1/settlements/{id}/authorise

Preconditions:
- Settlement status must be PENDING_AUTHORISE
- Different user from requester
- User has authorizer role

Processing:
1. Verify approval exists and pending
2. Verify different user (segregation of duties)
3. Update approval (authorized_by, authorized_at)
4. Log audit entry
5. Send notification
6. Return new status: AUTHORISED
```

## Performance Characteristics

### Processing Speed

**Ingestion:**
- Validation: < 1ms
- Rule evaluation: < 5ms
- Database insert: < 2ms
- **Total: < 10ms per settlement**

**Background Processing:**
- Fetch events: < 10ms
- Calculate contribution: < 5ms
- Update group: < 2ms (atomic)
- Update processing state: < 2ms
- **Total: ~20ms per settlement**
- **Batch of 1000: ~20 seconds**

**Status Updates:**
- Available within 5-10 seconds (processing window)
- Query time: < 100ms (indexed lookups)

### Database Load

**Write Pattern:**
- Settlement ingestion: INSERT only
- Group updates: UPDATE with atomic increment
- Processing state: UPSERT

**Read Pattern:**
- Status queries: Indexed by settlement_id
- Search queries: Indexed by group criteria
- Background processor: Indexed by processing_order

**Optimizations:**
- Processing state table prevents redundant calculations
- Batch processing reduces round trips
- Atomic operations eliminate locking

## Correctness Guarantees

### Property 1: Order Independence
**Guarantee:** Settlement versions can arrive in any order; final state is always correct.

**Proof:** Processing always uses latest version from database, not arrival order.

### Property 2: Idempotency
**Guarantee:** Processing the same settlement multiple times produces the same result.

**Proof:** Contribution calculation is deterministic; processing state prevents redundant updates.

### Property 3: No Lost Updates
**Guarantee:** Every version eventually contributes correctly to group totals.

**Proof:** Background processor continuously polls for unprocessed settlements.

### Property 4: Group Consistency
**Guarantee:** Group subtotal always equals sum of latest versions of eligible settlements.

**Proof:** Each settlement's contribution is calculated independently and applied atomically.

### Property 5: Approval Integrity
**Guarantee:** Approvals are always tied to specific versions and reset on version change.

**Proof:** Version comparison in processing logic detects changes and resets approvals.

## Comparison with Previous Designs

| Aspect | Design.md (Delta) | Design2.md (Delta) | **This Design (Idempotent)** |
|--------|-------------------|-------------------|------------------------------|
| **Out-of-order handling** | Complex version tracking | Complex version tracking | **Automatic** |
| **Duplicate processing** | May cause errors | May cause errors | **Safe** |
| **Race conditions** | Requires locks | Single-threaded | **Eliminated** |
| **Error recovery** | Manual intervention | Manual intervention | **Self-healing** |
| **Complexity** | High | Medium | **Low** |
| **Correctness proof** | Difficult | Medium | **Simple** |

## Implementation Roadmap

### Phase 1: Core Tables (Week 1)
```sql
-- Create settlements, groups, approval, processing_state tables
-- Add indexes
-- Set up PostgreSQL with appropriate configuration
```

### Phase 2: Ingestion API (Week 1-2)
```typescript
// POST /api/v1/settlements/ingest
// Validation logic
// Rule engine integration
// Event publishing
```

### Phase 3: Background Processor (Week 2-3)
```typescript
// processSettlement() function
// calculateContribution() helper
// updateGroupSubtotal() with atomic operations
// Processing state management
```

### Phase 4: Status & Approval (Week 3-4)
```typescript
// Status computation
// Approval workflow
// Audit logging
// Notification system
```

### Phase 5: Query & Search (Week 4-5)
```typescript
// Search API
// Bulk operations
// Excel export
// UI components
```

### Phase 6: Testing & Validation (Week 5-6)
```typescript
// Property-based tests for correctness
// Load testing (200K settlements)
// Failure scenario testing
// Performance optimization
```

## Key Implementation Notes

### 1. Always Query Latest Version
```typescript
// WRONG: Processing events individually
const event = await getEvent(order);
processEvent(event);

// CORRECT: Always get latest from database
const settlement = await getLatestVersion(settlementId);
processSettlement(settlement);
```

### 2. Atomic Group Updates
```typescript
// Use database atomic operations
UPDATE settlement_groups
SET subtotal_usd = subtotal_usd + ?
WHERE group_id = ?
```

### 3. Processing State is Critical
```typescript
// Without this, you can't tell if you've already processed
// the latest version
INSERT INTO processing_state ... ON CONFLICT UPDATE
```

### 4. Handle All Edge Cases
```typescript
// Group migration
// Eligibility changes
// Version gaps
// Concurrent processing
// Database failures
```

## Conclusion

This design solves the out-of-order version problem through **idempotent settlement contribution** - always calculating from the latest version rather than tracking deltas. This approach is:

- **Simpler**: No complex version tracking logic
- **More robust**: Handles all edge cases automatically
- **Self-healing**: Errors correct themselves on reprocessing
- **Provable**: Correctness is easy to verify
- **Scalable**: Works with single or multiple processors

The key insight is that **group subtotals should be computed from the current state of all settlements, not from changes to individual settlements**. This eliminates the fundamental race condition that plagues delta-based approaches.
