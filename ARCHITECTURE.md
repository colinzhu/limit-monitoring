# Hexagonal Architecture Documentation

## Overview

This project has been refactored to use **Hexagonal Architecture** (also known as Ports and Adapters Architecture). This architecture separates the core business logic from external concerns like databases, UI, and external APIs.

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                       │
│  (Primary Adapters - Driving the application)               │
│  - HTTP Controllers                                         │
│  - API Routers                                              │
│  - DTOs for HTTP requests/responses                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   APPLICATION LAYER                         │
│  (Use Cases - Business orchestration)                       │
│  - ProcessSettlementUseCase                                 │
│  - RecalculateUseCase                                       │
│  - QuerySettlementUseCase                                   │
│  - ApprovalUseCase                                          │
│  - Application DTOs                                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                     DOMAIN LAYER                            │
│  (Core Business Logic - Pure & Independent)                 │
│  - Entities (Settlement, RunningTotal, Activity)            │
│  - Value Objects (SettlementDirection, BusinessStatus, etc)│
│  - Domain Events (SettlementEvent)                          │
│  - Domain Services (Interfaces)                             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   INFRASTRUCTURE LAYER                      │
│  (Secondary Adapters - Driven by the application)           │
│  - Persistence (JDBC Repositories)                          │
│  - Messaging (Event Publishers)                             │
│  - External APIs (HTTP Clients)                             │
│  - Configuration (In-memory/External)                       │
└─────────────────────────────────────────────────────────────┘
```

## Directory Structure

```
src/main/java/com/tvpc/
├── domain/                          # DOMAIN LAYER
│   ├── model/                       # Entities and Value Objects
│   │   ├── Settlement.java
│   │   ├── RunningTotal.java
│   │   ├── Activity.java
│   │   ├── ExchangeRate.java
│   │   ├── SettlementDirection.java
│   │   ├── SettlementType.java
│   │   └── BusinessStatus.java
│   ├── event/                       # Domain Events
│   │   ├── SettlementEvent.java
│   │   └── DomainEventPublisher.java (interface)
│   └── service/                     # Domain Services (interfaces)
│
├── application/                     # APPLICATION LAYER
│   ├── usecase/                     # Use Case Implementations
│   │   ├── ProcessSettlementUseCaseImpl.java
│   │   ├── RecalculateUseCaseImpl.java
│   │   ├── QuerySettlementUseCaseImpl.java
│   │   └── ApprovalUseCaseImpl.java
│   ├── dto/                         # Application DTOs
│   │   ├── SettlementRequest.java
│   │   ├── SettlementResponse.java
│   │   └── ValidationResult.java
│   └── port/                        # Port Interfaces
│       ├── inbound/                 # Input Ports (Use Cases)
│       │   ├── ProcessSettlementUseCase.java
│       │   ├── RecalculateUseCase.java
│       │   ├── QuerySettlementUseCase.java
│       │   └── ApprovalUseCase.java
│       └── outbound/                # Output Ports (Repositories)
│           ├── SettlementRepository.java
│           ├── RunningTotalRepository.java
│           ├── ActivityRepository.java
│           ├── ExchangeRateRepository.java
│           ├── NotificationRepository.java
│           └── ConfigurationRepository.java
│
├── infrastructure/                  # INFRASTRUCTURE LAYER
│   ├── adapter/                     # Adapters
│   │   ├── inbound/                 # Primary Adapters (not used in this project)
│   │   └── outbound/                # Secondary Adapters
│   │       └── HttpNotificationRepository.java
│   ├── persistence/                 # Repository Implementations
│   │   ├── JdbcSettlementRepository.java
│   │   ├── JdbcRunningTotalRepository.java
│   │   ├── JdbcActivityRepository.java
│   │   └── JdbcExchangeRateRepository.java
│   ├── messaging/                   # Event Publishing
│   │   └── VertxEventPublisher.java
│   └── config/                      # Configuration
│       ├── InMemoryConfigurationRepository.java
│       └── HexagonalApplicationConfig.java
│
├── presentation/                    # PRESENTATION LAYER
│   ├── controller/                  # HTTP Controllers
│   │   ├── SettlementController.java
│   │   ├── HealthController.java
│   │   └── RecalculateController.java
│   ├── router/                      # HTTP Routing
│   │   └── ApiRouter.java
│   └── dto/                         # API DTOs
│       ├── ApiSettlementRequest.java
│       ├── ApiSettlementResponse.java
│       ├── RecalculateRequest.java
│       └── ApprovalRequest.java
│
├── HexagonalMain.java               # Entry Point
├── HexagonalHttpServerVerticle.java # HTTP Server
└── Main.java                        # Original entry point (kept for reference)
```

## Key Design Principles

### 1. Dependency Inversion
- **Domain Layer** defines interfaces (ports)
- **Infrastructure Layer** implements interfaces
- Dependencies point inward: Infrastructure → Application → Domain

### 2. Separation of Concerns
- **Domain**: Pure business logic, no external dependencies
- **Application**: Orchestrates use cases, coordinates between layers
- **Infrastructure**: Handles technical concerns (DB, HTTP, etc.)
- **Presentation**: Handles HTTP/API concerns

### 3. Testability
- Domain layer can be tested without any infrastructure
- Use cases can be tested with mock repositories
- Controllers can be tested with mock use cases

### 4. Flexibility
- Can swap database (Oracle → PostgreSQL) without changing domain
- Can add new UI (Web, Mobile, CLI) without changing business logic
- Can add new external services without affecting core

## Data Flow Example: Settlement Ingestion

```
HTTP Request (POST /api/settlements)
    ↓
SettlementController (Presentation Layer)
    ↓
SettlementRequest (API DTO) → Convert to Application DTO
    ↓
ProcessSettlementUseCase (Application Layer)
    ↓
Settlement (Domain Entity) + Validation
    ↓
SettlementRepository.save() (Port Interface)
    ↓
JdbcSettlementRepository (Infrastructure)
    ↓
Oracle Database
```

## Ports and Adapters

### Primary Ports (Inbound)
Driving adapters that trigger use cases:
- `ProcessSettlementUseCase` - Called by SettlementController
- `RecalculateUseCase` - Called by RecalculateController
- `QuerySettlementUseCase` - Called by query endpoints
- `ApprovalUseCase` - Called by approval endpoints

### Secondary Ports (Outbound)
Driven adapters that the use cases call:
- `SettlementRepository` - Database operations
- `RunningTotalRepository` - Aggregation operations
- `ActivityRepository` - Audit operations
- `ExchangeRateRepository` - Currency conversion
- `NotificationRepository` - External notifications
- `ConfigurationRepository` - Configuration management

## Benefits of This Architecture

1. **Maintainability**: Clear separation makes code easier to understand
2. **Testability**: Each layer can be tested independently
3. **Flexibility**: Easy to change implementations without affecting core
4. **Scalability**: Can add new features without modifying existing code
5. **Domain Purity**: Business logic is isolated from technical concerns

## Migration Notes

### Original Structure
```
com.tvpc/
├── domain/              # Entities
├── service/             # Business logic mixed with DB calls
├── repository/          # DB access
├── handler/             # HTTP handlers
└── processor/           # Event processors
```

### New Structure
```
com.tvpc/
├── domain/              # Pure entities + value objects
├── application/         # Use cases + ports
├── infrastructure/      # Adapters + implementations
├── presentation/        # HTTP controllers + routers
└── Main.java            # Entry point
```

### Key Changes
1. **Domain**: Moved to `domain/model/`, added rich behavior methods
2. **Services**: Split into use cases in `application/usecase/`
3. **Repositories**: Moved to `infrastructure/persistence/`, interfaces in `application/port/outbound/`
4. **Handlers**: Moved to `presentation/controller/`
5. **New**: Ports (interfaces) define contracts between layers

## Running the Application

### Using Hexagonal Architecture
```bash
mvn exec:java -Dexec.mainClass="com.tvpc.HexagonalMain"
```

### Using Original Architecture (still available)
```bash
mvn exec:java -Dexec.mainClass="com.tvpc.Main"
```

## Future Enhancements

1. **Add Query Use Cases**: Implement full search and query functionality
2. **Add Approval Use Cases**: Implement approval workflow
3. **Add Notification Use Cases**: Implement retry logic
4. **Add Event Sourcing**: Use events for audit trail
5. **Add CQRS**: Separate read and write models
6. **Add External Configuration**: Fetch limits and rules from external system
7. **Add Distributed Locking**: For concurrent settlement processing

## References

- **Hexagonal Architecture**: Alistair Cockburn (2005)
- **Domain-Driven Design**: Eric Evans
- **Clean Architecture**: Robert C. Martin
- **Ports and Adapters Pattern**: https://herbertograca.com/2017/11/16/ports-adapters-architecture/
