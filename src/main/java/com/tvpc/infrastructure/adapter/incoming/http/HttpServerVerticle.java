package com.tvpc.infrastructure.adapter.incoming.http;

import com.tvpc.application.port.incoming.SettlementIngestionUseCase;
import com.tvpc.application.service.SettlementIngestionService;
import com.tvpc.domain.port.configuration.ConfigurationService;
import com.tvpc.domain.port.messaging.EventPublisher;
import com.tvpc.domain.port.repository.ActivityRepository;
import com.tvpc.domain.port.repository.ExchangeRateRepository;
import com.tvpc.domain.port.repository.RunningTotalRepository;
import com.tvpc.domain.port.repository.SettlementRepository;
import com.tvpc.domain.service.SettlementValidator;
import com.tvpc.infrastructure.adapter.outgoing.configuration.InMemoryConfigurationService;
import com.tvpc.infrastructure.adapter.outgoing.messaging.VertxEventPublisher;
import com.tvpc.infrastructure.adapter.outgoing.repository.JdbcActivityRepository;
import com.tvpc.infrastructure.adapter.outgoing.repository.JdbcExchangeRateRepository;
import com.tvpc.infrastructure.adapter.outgoing.repository.JdbcRunningTotalRepository;
import com.tvpc.infrastructure.adapter.outgoing.repository.JdbcSettlementRepository;
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
 * This is the infrastructure adapter that wires all the components together
 */
public class HttpServerVerticle extends AbstractVerticle {
    private static final Logger log = LoggerFactory.getLogger(HttpServerVerticle.class);

    private static final int DEFAULT_PORT = 8080;

    private JDBCPool jdbcPool;
    private SettlementIngestionUseCase ingestionUseCase;
    private SettlementIngestionHandler ingestionHandler;

    @Override
    public void start(Promise<Void> startPromise) {
        log.info("Starting HTTP Server Verticle...");

        // Initialize database connection
        initializeDatabase()
                .compose(v -> {
                    log.info("Database initialized successfully");
                    // Initialize services
                    initializeServices();
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
        if (jdbcPool != null) {
            jdbcPool.close();
        }
        log.info("HTTP Server Verticle stopped");
    }

    private Future<Void> initializeDatabase() {
        try {
            // Get database configuration from application.yml
            JsonObject dbConfig = config().getJsonObject("database");
            if (dbConfig == null) {
                return Future.failedFuture("Database configuration not found in application.yml");
            }

            log.info("Connecting to database: {}", dbConfig.getString("url"));

            // Use JsonObject configuration for JDBCPool
            JsonObject poolConfig = new JsonObject()
                    .put("url", dbConfig.getString("url"))
                    .put("user", dbConfig.getString("user"))
                    .put("password", dbConfig.getString("password"))
                    .put("driver_class", dbConfig.getString("driver_class"))
                    .put("max_pool_size", dbConfig.getInteger("max_pool_size", 10));

            jdbcPool = io.vertx.jdbcclient.JDBCPool.pool(vertx, poolConfig);

            // Test connection (schema already created in Oracle)
            return jdbcPool.query("SELECT 1 FROM DUAL").execute()
                    .onSuccess(result -> log.info("Database connection test successful"))
                    .onFailure(error -> log.error("Database connection failed", error))
                    .mapEmpty();

        } catch (Exception e) {
            log.error("Error initializing database", e);
            return Future.failedFuture(e);
        }
    }

    private void initializeServices() {
        // Repositories (infrastructure adapters implementing domain ports)
        SettlementRepository settlementRepository = new JdbcSettlementRepository(jdbcPool);
        RunningTotalRepository runningTotalRepository = new JdbcRunningTotalRepository(jdbcPool);
        ExchangeRateRepository exchangeRateRepository = new JdbcExchangeRateRepository(jdbcPool);
        ActivityRepository activityRepository = new JdbcActivityRepository(jdbcPool);

        // Event publisher (infrastructure adapter implementing domain port)
        EventPublisher eventPublisher = new VertxEventPublisher(vertx);

        // Configuration service (infrastructure adapter implementing domain port)
        ConfigurationService configurationService = new InMemoryConfigurationService();

        // Validator (domain service)
        SettlementValidator validator = new SettlementValidator();

        // Ingestion service (application service implementing use case port)
        ingestionUseCase = new SettlementIngestionService(
                validator,
                settlementRepository,
                runningTotalRepository,
                activityRepository,
                eventPublisher,
                jdbcPool,
                configurationService
        );

        // Handler (infrastructure adapter)
        ingestionHandler = new SettlementIngestionHandler(ingestionUseCase);

        log.info("Services initialized successfully");
    }

    private Future<Void> startHttpServer() {
        Router router = Router.router(vertx);

        // Global handlers
        router.route().handler(LoggerHandler.create());
        router.route().handler(BodyHandler.create());

        // Setup routes
        SettlementRouter settlementRouter = new SettlementRouter(router, ingestionHandler);
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

        return vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(server -> log.info("HTTP server listening on port {}", port))
                .mapEmpty();
    }

    private int getPort() {
        return config().getInteger("http.port", DEFAULT_PORT);
    }
}
