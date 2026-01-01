package com.tvpc;

import com.tvpc.domain.ports.inbound.SettlementIngestionUseCase;
import com.tvpc.domain.ports.outbound.*;
import com.tvpc.infrastructure.config.InMemoryConfigurationService;
import com.tvpc.infrastructure.database.*;
import com.tvpc.infrastructure.messaging.VertxEventPublisher;
import com.tvpc.application.SettlementIngestionService;
import com.tvpc.domain.services.SettlementValidator;
import com.tvpc.interfaces.http.SettlementRouter;
import com.tvpc.interfaces.http.handlers.SettlementIngestionHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.jdbcclient.JDBCPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Server Verticle - handles all HTTP requests
 *
 * Refactored to use Hexagonal Architecture:
 * - Depends only on port interfaces (inbound/outbound)
 * - No direct infrastructure dependencies
 * - Composition root for wiring
 */
public class HttpServerVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(HttpServerVerticle.class);

    private static final int DEFAULT_PORT = 8080;

    private JDBCPool pool;
    private SettlementRouter settlementRouter;

    @Override
    public void start(Promise<Void> startPromise) {
        log.info("Starting HTTP Server Verticle (Hexagonal Architecture)...");

        // Initialize infrastructure and wire everything
        initializeInfrastructure()
                .compose(v -> {
                    log.info("Infrastructure initialized successfully");
                    // Create composition root and wire components
                    return wireApplication();
                })
                .compose(v -> {
                    // Start HTTP server
                    return startHttpServer();
                })
                .onSuccess(v -> {
                    log.info("HTTP Server Verticle started successfully on port {}", getPort());
                    startPromise.complete();
                })
                .onFailure(error -> {
                    log.error("Failed to start HTTP Server Verticle", error);
                    startPromise.fail(error);
                });
    }

    @Override
    public void stop() {
        if (pool != null) {
            pool.close();
        }
        log.info("HTTP Server Verticle stopped");
    }

    /**
     * Phase 1: Initialize infrastructure adapters
     */
    private Future<Void> initializeInfrastructure() {
        Promise<Void> promise = Promise.promise();

        try {
            // Get database configuration
            JsonObject dbConfig = config().getJsonObject("database");
            if (dbConfig == null) {
                promise.fail("Database configuration not found in application.yml");
                return promise.future();
            }

            log.info("Connecting to database: {}", dbConfig.getString("url"));

            // Create JDBCPool
            JsonObject poolConfig = new JsonObject()
                    .put("url", dbConfig.getString("url"))
                    .put("user", dbConfig.getString("user"))
                    .put("password", dbConfig.getString("password"))
                    .put("driver_class", dbConfig.getString("driver_class"))
                    .put("max_pool_size", dbConfig.getInteger("max_pool_size", 10));

            pool = JDBCPool.pool(vertx, poolConfig);

            // Test connection
            pool.query("SELECT 1 FROM DUAL").execute()
                    .onSuccess(result -> {
                        log.info("Database connection test successful");
                        promise.complete();
                    })
                    .onFailure(error -> {
                        log.error("Database connection failed", error);
                        promise.fail(error);
                    });

        } catch (Exception e) {
            log.error("Error initializing infrastructure", e);
            promise.fail(e);
        }

        return promise.future();
    }

    /**
     * Phase 2: Wire application layer (composition root)
     * Creates all components and injects dependencies
     */
    private Future<Void> wireApplication() {
        Promise<Void> promise = Promise.promise();

        try {
            // ==================== Infrastructure Layer (Adapters) ====================

            // Transaction Manager
            JdbcTransactionManager transactionManager = new JdbcTransactionManager(pool);

            // Repositories (Outbound Ports)
            SettlementRepositoryPort settlementRepository = new JdbcSettlementRepository(transactionManager);
            RunningTotalRepositoryPort runningTotalRepository = new JdbcRunningTotalRepository(transactionManager);
            ExchangeRateRepositoryPort exchangeRateRepository = new JdbcExchangeRateRepository(transactionManager);
            ActivityRepositoryPort activityRepository = new JdbcActivityRepository(transactionManager);

            // Event Publisher (Outbound Port)
            EventPublisherPort eventPublisher = new VertxEventPublisher(vertx);

            // Configuration Service (Outbound Port)
            ConfigurationServicePort configService = new InMemoryConfigurationService();

            // ==================== Domain Layer ====================

            // Domain Services
            SettlementValidator validator = new SettlementValidator();

            // ==================== Application Layer (Use Cases) ====================

            // Settlement Ingestion Use Case
            SettlementIngestionUseCase ingestionUseCase = new SettlementIngestionService(
                    validator,
                    settlementRepository,
                    runningTotalRepository,
                    exchangeRateRepository,
                    eventPublisher,
                    transactionManager,
                    configService
            );

            // ==================== Interface Layer (Primary Adapters) ====================

            // HTTP Handler
            SettlementIngestionHandler ingestionHandler = new SettlementIngestionHandler(ingestionUseCase);

            // Router
            settlementRouter = new SettlementRouter(
                    Router.router(vertx),
                    ingestionHandler,
                    settlementRepository,
                    exchangeRateRepository
            );

            log.info("Application wiring completed successfully");
            promise.complete();

        } catch (Exception e) {
            log.error("Error wiring application", e);
            promise.fail(e);
        }

        return promise.future();
    }

    /**
     * Phase 3: Start HTTP server with configured routes
     */
    private Future<Void> startHttpServer() {
        Promise<Void> promise = Promise.promise();

        Router router = settlementRouter.getRouter();

        // Global handlers
        router.route().handler(LoggerHandler.create());
        router.route().handler(BodyHandler.create());

        // Setup all routes
        settlementRouter.setupRoutes();

        // Default route - 404
        router.route().handler(ctx -> {
            ctx.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(new JsonObject()
                            .put("status", "error")
                            .put("message", "Endpoint not found")
                            .encode()
                    );
        });

        int port = getPort();

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> {
                    log.info("HTTP server listening on port {}", port);
                    promise.complete();
                })
                .onFailure(promise::fail);

        return promise.future();
    }

    private int getPort() {
        return config().getInteger("http.port", DEFAULT_PORT);
    }
}
