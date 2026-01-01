package com.tvpc;

import com.tvpc.application.RunningTotalProcessorService;
import com.tvpc.application.SettlementIngestionService;
import com.tvpc.domain.ports.inbound.RunningTotalProcessorUseCase;
import com.tvpc.domain.ports.inbound.SettlementIngestionUseCase;
import com.tvpc.domain.ports.outbound.*;
import com.tvpc.domain.services.SettlementValidator;
import com.tvpc.infrastructure.config.InMemoryConfigurationService;
import com.tvpc.infrastructure.database.*;
import com.tvpc.infrastructure.messaging.VertxEventConsumer;
import com.tvpc.infrastructure.messaging.VertxEventPublisher;
import com.tvpc.interfaces.events.SettlementEventConsumerVerticle;
import com.tvpc.interfaces.http.SettlementRouter;
import com.tvpc.interfaces.http.handlers.SettlementIngestionHandler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.jdbcclient.JDBCPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composition Root for Hexagonal Architecture.
 *
 * Single responsibility: Create and wire all application components.
 * This is the ONLY place where concrete infrastructure implementations are instantiated.
 *
 * Benefits:
 * - Clear separation of concerns
 * - Easy to test (can mock at boundary)
 * - Easy to swap implementations
 * - Single point of configuration
 */
public class CompositionRoot {

    private static final Logger log = LoggerFactory.getLogger(CompositionRoot.class);

    private final Vertx vertx;
    private final JsonObject config;
    private JDBCPool pool;

    public CompositionRoot(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }

    /**
     * Creates and wires the HTTP Server Verticle.
     * This is the primary entry point for HTTP requests.
     */
    public HttpServerVerticle createHttpServerVerticle() {
        log.info("Creating HttpServerVerticle with Hexagonal wiring...");

        // Initialize infrastructure first
        initializeInfrastructure();

        // Wire all layers
        wireComponents();

        // Return verticle that uses the wired components
        return new HttpServerVerticle() {
            @Override
            public void start(io.vertx.core.Promise<Void> startPromise) {
                // Override to use pre-wired components
                // The verticle will initialize infrastructure and wire again
                // But we could refactor to pass components to constructor
                super.start(startPromise);
            }
        };
    }

    /**
     * Creates and wires the Event Consumer Verticle.
     * This consumes events from the event bus for async processing.
     */
    public SettlementEventConsumerVerticle createEventConsumerVerticle() {
        log.info("Creating SettlementEventConsumerVerticle with Hexagonal wiring...");

        // Initialize infrastructure if not already done
        if (pool == null) {
            initializeInfrastructure();
        }

        // Create infrastructure adapters
        JdbcTransactionManager transactionManager = new JdbcTransactionManager(pool);

        SettlementRepositoryPort settlementRepository = new JdbcSettlementRepository(transactionManager);
        RunningTotalRepositoryPort runningTotalRepository = new JdbcRunningTotalRepository(transactionManager);
        ExchangeRateRepositoryPort exchangeRateRepository = new JdbcExchangeRateRepository(transactionManager);

        // Create application service
        RunningTotalProcessorUseCase processorUseCase = new RunningTotalProcessorService(
                settlementRepository,
                runningTotalRepository,
                exchangeRateRepository,
                transactionManager
        );

        // Create event consumer port
        VertxEventConsumer eventConsumer = new VertxEventConsumer(vertx, processorUseCase);

        // Return verticle wrapper
        return new SettlementEventConsumerVerticle(eventConsumer);
    }

    /**
     * Creates a fully wired HTTP handler (useful for testing or direct use).
     */
    public SettlementIngestionHandler createSettlementIngestionHandler() {
        if (pool == null) {
            initializeInfrastructure();
        }

        SettlementIngestionUseCase useCase = createIngestionUseCase();
        return new SettlementIngestionHandler(useCase);
    }

    /**
     * Creates a fully wired router (useful for testing or direct use).
     */
    public SettlementRouter createSettlementRouter() {
        if (pool == null) {
            initializeInfrastructure();
        }

        JdbcTransactionManager transactionManager = new JdbcTransactionManager(pool);
        SettlementRepositoryPort settlementRepository = new JdbcSettlementRepository(transactionManager);
        ExchangeRateRepositoryPort exchangeRateRepository = new JdbcExchangeRateRepository(transactionManager);

        SettlementIngestionHandler handler = createSettlementIngestionHandler();
        Router router = Router.router(vertx);
        return new SettlementRouter(router, handler, settlementRepository, exchangeRateRepository);
    }

    // ==================== Internal Wiring Methods ====================

    /**
     * Phase 1: Initialize infrastructure adapters.
     * Creates JDBCPool and tests connection.
     */
    private void initializeInfrastructure() {
        try {
            JsonObject dbConfig = config.getJsonObject("database");
            if (dbConfig == null) {
                throw new RuntimeException("Database configuration not found");
            }

            log.info("Initializing infrastructure with database: {}", dbConfig.getString("url"));

            JsonObject poolConfig = new JsonObject()
                    .put("url", dbConfig.getString("url"))
                    .put("user", dbConfig.getString("user"))
                    .put("password", dbConfig.getString("password"))
                    .put("driver_class", dbConfig.getString("driver_class"))
                    .put("max_pool_size", dbConfig.getInteger("max_pool_size", 10));

            pool = JDBCPool.pool(vertx, poolConfig);

            // Test connection synchronously for simplicity
            // In production, you might want async initialization
            pool.query("SELECT 1 FROM DUAL").execute()
                    .onSuccess(result -> log.info("Database connection successful"))
                    .onFailure(error -> {
                        log.error("Database connection failed", error);
                        throw new RuntimeException("Database initialization failed", error);
                    });

        } catch (Exception e) {
            log.error("Failed to initialize infrastructure", e);
            throw e;
        }
    }

    /**
     * Phase 2: Wire all components together.
     * This method demonstrates the complete dependency graph.
     */
    private void wireComponents() {
        log.info("Wiring application components...");

        // This method is primarily for demonstration/logging
        // In practice, components are created on-demand in factory methods

        // Infrastructure Layer
        JdbcTransactionManager transactionManager = new JdbcTransactionManager(pool);
        JdbcSettlementRepository settlementRepository = new JdbcSettlementRepository(transactionManager);
        JdbcRunningTotalRepository runningTotalRepository = new JdbcRunningTotalRepository(transactionManager);
        JdbcExchangeRateRepository exchangeRateRepository = new JdbcExchangeRateRepository(transactionManager);
        JdbcActivityRepository activityRepository = new JdbcActivityRepository(transactionManager);
        VertxEventPublisher eventPublisher = new VertxEventPublisher(vertx);
        InMemoryConfigurationService configService = new InMemoryConfigurationService();

        // Domain Layer
        SettlementValidator validator = new SettlementValidator();

        // Application Layer
        SettlementIngestionService ingestionService = new SettlementIngestionService(
                validator,
                settlementRepository,
                runningTotalRepository,
                exchangeRateRepository,
                eventPublisher,
                transactionManager,
                configService
        );

        RunningTotalProcessorService processorService = new RunningTotalProcessorService(
                settlementRepository,
                runningTotalRepository,
                exchangeRateRepository,
                transactionManager
        );

        // Interface Layer
        SettlementIngestionHandler handler = new SettlementIngestionHandler(ingestionService);
        SettlementRouter router = new SettlementRouter(Router.router(vertx), handler, settlementRepository, exchangeRateRepository);

        log.info("All components wired successfully");
    }

    /**
     * Creates the SettlementIngestionUseCase with all dependencies.
     */
    private SettlementIngestionUseCase createIngestionUseCase() {
        JdbcTransactionManager transactionManager = new JdbcTransactionManager(pool);

        SettlementRepositoryPort settlementRepository = new JdbcSettlementRepository(transactionManager);
        RunningTotalRepositoryPort runningTotalRepository = new JdbcRunningTotalRepository(transactionManager);
        ExchangeRateRepositoryPort exchangeRateRepository = new JdbcExchangeRateRepository(transactionManager);
        EventPublisherPort eventPublisher = new VertxEventPublisher(vertx);
        ConfigurationServicePort configService = new InMemoryConfigurationService();

        SettlementValidator validator = new SettlementValidator();

        return new SettlementIngestionService(
                validator,
                settlementRepository,
                runningTotalRepository,
                exchangeRateRepository,
                eventPublisher,
                transactionManager,
                configService
        );
    }

    /**
     * Get the underlying JDBC pool (for testing or advanced scenarios).
     */
    public JDBCPool getPool() {
        return pool;
    }

    /**
     * Get the configuration.
     */
    public JsonObject getConfig() {
        return config;
    }

    /**
     * Cleanup resources.
     */
    public void close() {
        if (pool != null) {
            pool.close();
            log.info("CompositionRoot resources cleaned up");
        }
    }
}
