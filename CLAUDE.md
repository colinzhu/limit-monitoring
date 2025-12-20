# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository Overview

This repository contains design documentation and specifications for a **Payment Limit Monitoring System** - a financial risk management application that monitors settlement flows from trading systems, calculates aggregated exposure by counterparty and value date, and flags transactions exceeding predefined limits.

**Note**: This is a design/documentation repository. No actual implementation code exists yet - only architectural specifications and requirements.

## Project Structure

```
.tvpc/
├── .kiro/specs/payment-limit-monitoring/
│   ├── requirements.md      # Detailed functional requirements (9 requirements)
│   ├── design.md            # Architecture design with correctness properties (30 properties)
│   └── design2.md           # Practical implementation guide with database schemas
├── .xcodemap/
│   └── config/xcodemap-class-filter.yaml  # Code mapping configuration
└── .git/                    # Version control
```

## Key Design Documents

### 1. Requirements (`.kiro/specs/payment-limit-monitoring/requirements.md`)
Defines 9 functional requirements covering:
- Settlement ingestion and validation
- Group aggregation by PTS, Processing Entity, Counterparty, Value Date
- Status management (CREATED, BLOCKED, PENDING_AUTHORISE, AUTHORISED)
- Two-step approval workflow with segregation of duties
- Search and filtering capabilities
- API for external system queries
- Configuration management (limits, exchange rates)
- Audit and compliance requirements

**Performance Target**: Process 200,000 settlements within 30 minutes during peak periods.

### 2. Design Document (`.kiro/specs/payment-limit-monitoring/design.md`)
- **Architecture**: Event-driven with single-threaded background processor
- **Key Patterns**: Event sourcing, materialized views, dynamic status computation
- **Correctness Properties**: 30 formal properties that must hold true
- **Concurrency Strategy**: Atomic operations to avoid race conditions
- **Performance Optimizations**:
  - No stored status fields (computed on-demand)
  - No stored USD equivalents (converted on-demand)
  - Group-level updates only (no mass settlement updates)

### 3. Design2 Document (`.kiro/specs/payment-limit-monitoring/design2.md`)
Practical implementation guide with:
- **Database Schema**: PostgreSQL tables for settlements, groups, approvals, audit, config
- **Service Layer**: Detailed API contracts and processing flows
- **API Endpoints**: Complete request/response examples
- **Deployment Strategy**: Infrastructure requirements and CI/CD pipeline
- **Testing Strategy**: Unit tests, property-based tests, load testing

## High-Level Architecture

```
External Systems (PTS, Rules, Exchange Rates)
         ↓
Settlement Ingestion Service (REST API)
         ↓
Event Store (PostgreSQL - immutable settlements)
         ↓
Background Processor (single-threaded, sequential)
         ↓
Materialized Views (Group subtotals, statuses)
         ↓
Query Layer (UI + External API)
```

## Core Concepts

### Settlement Lifecycle
1. **Ingestion**: Receive settlement → Validate → Apply filtering rules → Store with version
2. **Processing**: Background processor calculates USD amount → Updates group subtotal
3. **Status**: Computed dynamically from group subtotal vs exposure limit
4. **Approval**: Two-step workflow (Request → Authorise) with audit trail
5. **Versioning**: New versions reset approvals, recalculate group totals

### Key Design Decisions
- **No stored status fields**: CREATED/BLOCKED computed on-demand to avoid mass updates
- **No stored USD equivalents**: Currency conversion uses latest rates at query time
- **Immutable settlements**: Never update, only add new versions
- **Group-level aggregation**: Subtotals maintained at group level, not per settlement
- **Single-threaded processing**: Eliminates race conditions entirely

### Status Computation
```
CREATED:     Group subtotal ≤ limit AND no approval
BLOCKED:     Group subtotal > limit AND no approval
PENDING_AUTHORISE:  Approval requested, not authorized
AUTHORISED:  Both request and authorize completed
```

## Common Development Tasks

### Understanding the System
1. Read `requirements.md` for functional requirements
2. Read `design.md` for architecture patterns and correctness properties
3. Read `design2.md` for concrete implementation details

### Implementation Planning
When implementing this system, you would need to:

**Phase 1: Core Ingestion**
- Create PostgreSQL database with tables from design2.md
- Implement settlement ingestion API with validation
- Apply filtering rules and store settlements

**Phase 2: Background Processing**
- Build single-threaded processor that reads settlements sequentially
- Calculate USD amounts using exchange rates
- Update group subtotals using atomic operations

**Phase 3: Approval Workflow**
- Implement two-step approval with segregation of duties
- Create audit logging
- Handle status transitions

**Phase 4: Search & UI**
- Build search API with filtering
- Create web UI for operators
- Implement bulk operations and Excel export

**Phase 5: External Integration**
- Status query API for external systems
- Notification system for authorization events
- Manual recalculation endpoints

### Testing Strategy
- **Unit tests**: Component integration, edge cases, business logic
- **Property-based tests**: Verify all 30 correctness properties using fast-check
- **Load tests**: 200K settlements in 30 minutes, burst traffic scenarios

## Technology Stack (Recommended)

- **Database**: PostgreSQL with read replicas
- **Caching**: Redis for rates, rules, and status caching
- **API**: REST with OpenAPI specification
- **Message Queue**: Kafka (optional, for high-volume ingestion)
- **Language**: TypeScript/Node.js or similar

## Important Considerations

### Performance Requirements
- Ingestion: < 10ms per settlement
- Status updates: Available within 5-10 seconds
- API queries: < 3 seconds p99
- Bulk processing: 200K settlements / 30 minutes

### Concurrency & Race Conditions
The design specifically addresses race conditions through:
1. **Single-threaded background processor** - no concurrent updates
2. **Atomic increment operations** - for group subtotal updates
3. **Idempotent version processing** - handles out-of-order arrivals
4. **Distributed locks** - for complex recalculation scenarios

### Compliance & Security
- Immutable audit trail for all actions
- Segregation of duties (different users for request/authorize)
- Data retention: 7 years for settlements and audits
- Encryption at rest and in transit

## Next Steps for Implementation

This repository is currently in the design phase. To begin implementation:

1. Choose technology stack (language, framework, database)
2. Set up development environment with PostgreSQL and Redis
3. Implement database schema from design2.md
4. Build settlement ingestion API
5. Create background processor service
6. Implement approval workflow
7. Add search and reporting capabilities
8. Set up monitoring and alerting

The design documents provide comprehensive specifications for building a production-ready system.
