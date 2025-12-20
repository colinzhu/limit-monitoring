# Settlement Subtotal Calculation - Core Idea

## Problem Statement
Calculate settlement group subtotals correctly and efficiently, handling:
- High volume (200K settlements in 30 minutes)
- Out-of-order arrival
- Settlement version updates
- Concurrent processing

## Core Idea

### 1. Sequence-Based Ordering
- Each settlement gets a monotonically increasing sequence ID
- Sequence IDs ensure processing order
- Used for tracking what's been processed

### 2. Optimistic Concurrency Control
- Calculations include sequence ID in result
- Save only if current sequence in DB ≤ calculation's sequence
- Prevents overwriting newer results

### 3. Version Management
- When new version arrives, mark old version as inactive
- Only active versions contribute to subtotal
- Maintain full history for audit

### 4. Processing State Tracking
- Track what each settlement currently contributes
- Enables incremental updates (not full recalculation)
- Allows idempotent processing

## Database Schema

```sql
-- Settlements (immutable history)
CREATE TABLE settlements (
    settlement_id VARCHAR(100),
    settlement_version INTEGER,
    sequence_id BIGSERIAL,  -- Auto-incrementing, ensures order
    pts VARCHAR(50),
    processing_entity VARCHAR(100),
    counterparty_id VARCHAR(100),
    value_date DATE,
    currency VARCHAR(3),
    amount DECIMAL(20, 2),
    is_eligible BOOLEAN,
    is_old BOOLEAN DEFAULT FALSE,  -- Marked true when newer version exists
    received_at TIMESTAMP,

    PRIMARY KEY (settlement_id, settlement_version),
    INDEX idx_sequence (sequence_id),
    INDEX idx_active (settlement_id, is_old) WHERE is_old = FALSE
);

-- Processing State (tracks current contribution)
CREATE TABLE settlement_processing_state (
    settlement_id VARCHAR(100) PRIMARY KEY,
    last_processed_version INTEGER,
    last_processed_contribution_usd DECIMAL(20, 2),
    last_processed_sequence_id BIGINT,
    group_id VARCHAR(255)
);
00
-- Group Subtotals (materialized aggregates)
CREATE TABLE settlement_groups (
    group_id VARCHAR(255) PRIMARY KEY,
    pts VARCHAR(50),
    processing_entity VARCHAR(100),
    counterparty_id VARCHAR(100),
    value_date DATE,
    subtotal_usd DECIMAL(20, 2) DEFAULT 0,
    settlement_count INTEGER DEFAULT 0,
    exposure_limit DECIMAL(20, 2),
    last_calculated_sequence_id BIGINT,
    last_updated TIMESTAMP,
    version INTEGER DEFAULT 0  -- Optimistic locking
);
```

## Processing Algorithm

```typescript
async function processSettlement(settlementId: string) {
    // 1. Get latest version (highest version number)
    const latest = await db.query(`
        SELECT * FROM settlements
        WHERE settlement_id = ?
        ORDER BY settlement_version DESC
        LIMIT 1
    `, [settlementId]);

    // 2. Get current processing state
    const state = await db.query(`
        SELECT last_processed_version, last_processed_contribution_usd
        FROM settlement_processing_state
        WHERE settlement_id = ?
    `, [settlementId]);

    const currentVersion = state?.last_processed_version;
    const currentContribution = state?.last_processed_contribution_usd || 0;

    // 3. Skip if already up-to-date
    if (latest.settlement_version === currentVersion) {
        return;  // Idempotent
    }

    // 4. Calculate latest contribution
    const latestContribution = latest.is_eligible
        ? await convertToUSD(latest.amount, latest.currency)
        : 0;

    // 5. Calculate delta
    const delta = latestContribution - currentContribution;

    // 6. Update group subtotal (atomic)
    const groupId = buildGroupId(latest);
    await db.query(`
        UPDATE settlement_groups
        SET subtotal_usd = subtotal_usd + ?,
            settlement_count = settlement_count + ?,
            version = version + 1,
            last_updated = NOW(),
            last_calculated_sequence_id = ?
        WHERE group_id = ?
    `, [delta, delta > 0 ? 1 : 0, latest.sequence_id, groupId]);

    // 7. Update processing state
    await db.query(`
        INSERT INTO settlement_processing_state
        (settlement_id, last_processed_version, last_processed_contribution_usd,
         last_processed_sequence_id, group_id)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (settlement_id)
        DO UPDATE SET
            last_processed_version = EXCLUDED.last_processed_version,
            last_processed_contribution_usd = EXCLUDED.last_processed_contribution_usd,
            last_processed_sequence_id = EXCLUDED.last_processed_sequence_id,
            group_id = EXCLUDED.group_id
    `, [settlementId, latest.settlement_version, latestContribution,
        latest.sequence_id, groupId]);

    // 8. Reset approvals if version changed
    if (currentVersion && latest.settlement_version !== currentVersion) {
        await resetApprovals(settlementId, latest.settlement_version);
    }
}
```

## Examples

### Example 1: Sequential Processing (Happy Path)

```
Initial State:
- Settlement X v1: seq=1, 80M, is_old=false, is_eligible=true
- Group A: subtotal=80M, last_seq=1

Processing:
1. Settlement Y v1 arrives (seq=2, 100M)
   - Calculate: 100M contribution
   - Delta: +100M
   - Group A: 80M + 100M = 180M, last_seq=2 ✓

2. Settlement X v2 arrives (seq=3, 90M)
   - Mark X v1 as is_old=true
   - Calculate: 90M contribution
   - Delta: 90M - 80M = +10M
   - Group A: 180M + 10M = 190M, last_seq=3 ✓

Result: 190M (90M + 100M) ✓ CORRECT
```

### Example 2: Out-of-Order Version Arrival

```
Initial State:
- Settlement X v1: seq=1, 80M, is_old=false
- Group A: subtotal=80M

Timeline:
T1: Settlement X v3 arrives (seq=3, 90M)
    - Mark X v1 as is_old=true
    - Insert X v3: seq=3, 90M, is_old=false
    - Calculate: 90M contribution
    - Delta: 90M - 80M = +10M
    - Group A: 80M + 10M = 90M ✓
    - Processing state: X → v3, 90M

T2: Settlement X v2 arrives (seq=2, 120M) - STALE!
    - Mark versions < v2 as old: X v1 already marked
    - Insert X v2: seq=2, 120M, is_old=false
    - Calculate: 120M contribution
    - Current state: X → v3, 90M
    - Delta: 120M - 90M = +30M
    - Group A: 90M + 30M = 120M ✗ WRONG!

Problem: Now we have TWO active versions (v2 and v3)!
```

**Solution Needed**: Ensure only ONE version is is_old=false at any time.

### Example 3: Concurrent Processing (Race Condition)

```
Initial State:
- Settlement X v1: seq=1, 80M, is_old=false
- Group A: subtotal=80M

Two concurrent requests:
Request A: Update to v2 (90M)
Request B: Update to v3 (120M)

Request A:
1. Read: current = v1 (80M)
2. Mark v1 as old
3. Insert v2: seq=2, 90M, is_old=false
4. Calculate: 90M
5. Delta: +10M
6. Group A: 80M + 10M = 90M

Request B (overlapping):
1. Read: current = v1 (80M) - BEFORE A completes!
2. Mark v1 as old (already marked)
3. Insert v3: seq=3, 120M, is_old=false
4. Calculate: 120M
5. Delta: +40M
6. Group A: 90M + 40M = 130M ✗ WRONG!

Final State:
- v1: is_old=true
- v2: is_old=false, 90M
- v3: is_old=false, 120M
- Group A: 130M (should be 120M)
```

**Problem**: Both see v1 as current, both calculate deltas from 80M.

### Example 4: Group Migration

```
Initial State:
- Settlement X v1: seq=1, 80M, Group A
- Group A: subtotal=80M

Settlement X v2 arrives with changed counterparty:
- New group: Group B
- Mark v1 as old
- Insert v2: seq=2, 90M, Group B

Processing:
1. Calculate v2 contribution: 90M
2. Current state: X → v1, 80M, Group A
3. Delta: +90M for Group B, -80M for Group A
4. Group A: 80M - 80M = 0M
5. Group B: 0M + 90M = 90M ✓

Result: Settlement moved correctly between groups
```

### Example 5: Idempotency (Duplicate Processing)

```
Initial State:
- Settlement X v1: seq=1, 80M
- Processing state: X → v1, 80M
- Group A: subtotal=80M

Process X v1 twice:
First processing:
1. Latest = v1 (80M)
2. Current = v1 (80M)
3. Delta = 0 → SKIP ✓

Second processing:
1. Latest = v1 (80M)
2. Current = v1 (80M)
3. Delta = 0 → SKIP ✓

Result: No change, system stable ✓
```

### Example 6: Eligibility Change

```
Initial State:
- Settlement X v1: seq=1, 80M, is_eligible=true
- Group A: subtotal=80M

Rule changes, Settlement X becomes ineligible:
- Settlement X v2 arrives: seq=2, 80M, is_eligible=false

Processing:
1. Mark v1 as old
2. Insert v2: is_eligible=false
3. Calculate v2 contribution: 0M (not eligible)
4. Current state: X → v1, 80M
5. Delta: 0M - 80M = -80M
6. Group A: 80M - 80M = 0M ✓

Result: Settlement removed from subtotal ✓
```

## Key Insights

### Why This Approach Works

1. **Sequence IDs provide ordering** - know what arrived when
2. **Processing state tracks contribution** - no recalculation needed
3. **Delta updates are atomic** - no race conditions
4. **Idempotent by design** - safe to reprocess
5. **Version-aware** - handles updates correctly

### What Makes It Fail

1. **Multiple active versions** - can have is_old=false for multiple versions
2. **No locking** - concurrent updates can see same "current" state
3. **Timing issues** - calculation vs. state update gaps

### The Fix

**Option A: Application Locking**
```typescript
acquireLock(settlementId);
try {
    await processSettlement(settlementId);
} finally {
    releaseLock(settlementId);
}
```

**Option B: Single Active Version Constraint**
```sql
-- Ensure only one version is is_old=false
CREATE UNIQUE INDEX idx_one_active
ON settlements(settlement_id)
WHERE is_old = FALSE;
```

**Option C: Background Processor (Recommended)**
```typescript
// Single-threaded processor
// Processes settlements one at a time
// No concurrency issues
```

## Performance Characteristics

### Time Complexity
- **Ingestion**: O(1) - just insert
- **Processing**: O(1) per settlement - one delta calculation
- **Total for 200K**: O(200K) = 200K operations ✓

### Space Complexity
- **Settlements**: 200K * avg_versions
- **Processing state**: 200K records
- **Groups**: ~number of unique groups

### Comparison with Full Recalculation
```
Your approach (with fixes):
- Each settlement: O(1)
- 200K settlements: 200K operations

Full recalculation:
- Each settlement: O(n) where n = current count
- 200K settlements: O(n²) = 40 billion operations
```

## Implementation Roadmap

### Phase 1: Basic Structure
1. Create tables with sequence IDs
2. Implement ingestion API
3. Add is_old flag logic

### Phase 2: Processing Logic
1. Add processing_state table
2. Implement delta calculation
3. Add idempotent updates

### Phase 3: Concurrency Handling
1. Add locking mechanism OR
2. Implement single-threaded processor
3. Add unique constraint for active versions

### Phase 4: Edge Cases
1. Handle group migrations
2. Handle eligibility changes
3. Handle version resets

## Open Questions

1. **How to prevent multiple active versions?**
   - Unique constraint?
   - Application locking?
   - Single-threaded processor?

2. **How to handle calculation failures?**
   - Retry logic?
   - Dead letter queue?
   - Manual intervention?

3. **How to verify correctness?**
   - Periodic full recalculation?
   - Checksum validation?
   - Audit queries?

4. **How to scale beyond single processor?**
   - Partition by group_id?
   - Shard by settlement_id?
   - Distributed locks?

## Next Steps

1. Choose concurrency handling strategy
2. Implement prototype
3. Test with out-of-order scenarios
4. Measure performance
5. Add monitoring and validation 
