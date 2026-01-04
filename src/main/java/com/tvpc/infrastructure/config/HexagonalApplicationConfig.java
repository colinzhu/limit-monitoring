package com.tvpc.infrastructure.config;

import com.tvpc.application.port.inbound.ProcessSettlementUseCase;
import com.tvpc.application.port.inbound.RecalculateUseCase;
import com.tvpc.application.port.outbound.*;
import com.tvpc.application.usecase.ProcessSettlementUseCaseImpl;
import com.tvpc.application.usecase.RecalculateUseCaseImpl;
import com.tvpc.infrastructure.persistence.*;
import com.tvpc.infrastructure.messaging.VertxEventPublisher;
import com.tvpc.infrastructure.adapter.outbound.HttpNotificationRepository;
import com.tvpc.presentation.controller.HealthController;
import com.tvpc.presentation.controller.RecalculateController;
import com.tvpc.presentation.controller.SettlementController;
import com.tvpc.presentation.router.ApiRouter;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.jdbcclient.JDBCPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hexagonal Application Configuration
 * Wires all components together (Dependency Injection)
 *
 * This class is responsible for:
 * 1. Creating infrastructure adapters (repositories, publishers)
 * 2. Creating use case implementations
 * 3. Creating presentation controllers
 * 4. Wiring everything together
 */
public class HexagonalApplicationConfig {
    private static final Logger log = LoggerFactory.getLogger(HexagonalApplicationConfig.class);

    private final Vertx vertx;
    private final JsonObject config;
    private final JDBCPool jdbcPool;

    // Infrastructure adapters (outbound)
    private SettlementRepository settlementRepository;
    private RunningTotalRepository runningTotalRepository;
    private ActivityRepository activityRepository;
    private ExchangeRateRepository exchangeRateRepository;
    private NotificationRepository notificationRepository;
    private ConfigurationRepository configurationRepository;

    // Infrastructure adapters (event publishing)
    private VertxEventPublisher eventPublisher;

    // Use cases (inbound)
    private ProcessSettlementUseCase processSettlementUseCase;
    private RecalculateUseCase recalculateUseCase;

    // Presentation controllers
    private SettlementController settlementController;
    private HealthController healthController;
    private RecalculateController recalculateController;

    // Router
    private ApiRouter apiRouter;

    public HexagonalApplicationConfig(Vertx vertx, JsonObject config, JDBCPool jdbcPool) {
        this.vertx = vertx;
        this.config = config;
        this.jdbcPool = jdbcPool;
    }

    /**
     * Initialize all components
     */
    public void initialize() {
        log.info("Initializing Hexagonal Architecture components...");

        // 1. Create infrastructure adapters (outbound ports)
        createInfrastructureAdapters();

        // 2. Create event publisher
        createEventPublisher();

        // 3. Create use cases (inbound ports)
        createUseCases();

        // 4. Create presentation controllers
        createControllers();

        // 5. Create router
        createRouter();

        log.info("Hexagonal Architecture initialized successfully");
    }

    private void createInfrastructureAdapters() {
        log.info("Creating infrastructure adapters...");

        // Repository adapters
        this.settlementRepository = new JdbcSettlementRepository(jdbcPool);
        this.runningTotalRepository = new JdbcRunningTotalRepository(jdbcPool);
        this.activityRepository = new JdbcActivityRepository(jdbcPool);
        this.exchangeRateRepository = new JdbcExchangeRateRepository(jdbcPool);

        // Configuration adapter
        this.configurationRepository = new InMemoryConfigurationRepository();

        // Notification adapter
        String notificationUrl = config.getString("external.notification.url", "");
        this.notificationRepository = new HttpNotificationRepository(vertx, notificationUrl);

        log.info("Infrastructure adapters created");
    }

    private void createEventPublisher() {
        log.info("Creating event publisher...");
        this.eventPublisher = new VertxEventPublisher(vertx);
        log.info("Event publisher created");
    }

    private void createUseCases() {
        log.info("Creating use cases...");

        // Process Settlement Use Case
        this.processSettlementUseCase = new ProcessSettlementUseCaseImpl(
                settlementRepository,
                runningTotalRepository,
                activityRepository,
                configurationRepository,
                jdbcPool
        );

        // Recalculate Use Case
        this.recalculateUseCase = new RecalculateUseCaseImpl(
                settlementRepository,
                runningTotalRepository,
                activityRepository,
                jdbcPool
        );

        log.info("Use cases created");
    }

    private void createControllers() {
        log.info("Creating controllers...");

        this.settlementController = new SettlementController(processSettlementUseCase);
        this.healthController = new HealthController();
        this.recalculateController = new RecalculateController(recalculateUseCase);

        log.info("Controllers created");
    }

    private void createRouter() {
        log.info("Creating router...");

        Router router = Router.router(vertx);
        this.apiRouter = new ApiRouter(router, settlementController, healthController, recalculateController);
        this.apiRouter.setupRoutes();

        log.info("Router created");
    }

    // Getters
    public SettlementController getSettlementController() {
        return settlementController;
    }

    public HealthController getHealthController() {
        return healthController;
    }

    public RecalculateController getRecalculateController() {
        return recalculateController;
    }

    public SettlementRepository getSettlementRepository() {
        return settlementRepository;
    }

    public RunningTotalRepository getRunningTotalRepository() {
        return runningTotalRepository;
    }

    public ProcessSettlementUseCase getProcessSettlementUseCase() {
        return processSettlementUseCase;
    }

    public RecalculateUseCase getRecalculateUseCase() {
        return recalculateUseCase;
    }

    public Router getRouter() {
        return apiRouter != null ? apiRouter.getRouter() : null;
    }
}
