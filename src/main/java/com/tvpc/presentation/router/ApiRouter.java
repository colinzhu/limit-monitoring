package com.tvpc.presentation.router;

import com.tvpc.presentation.controller.HealthController;
import com.tvpc.presentation.controller.RecalculateController;
import com.tvpc.presentation.controller.SettlementController;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.LoggerHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * API Router - Configures all HTTP routes
 * Presentation layer
 */
public class ApiRouter {
    private static final Logger log = LoggerFactory.getLogger(ApiRouter.class);

    private final Router router;
    private final SettlementController settlementController;
    private final HealthController healthController;
    private final RecalculateController recalculateController;

    public ApiRouter(
            Router router,
            SettlementController settlementController,
            HealthController healthController,
            RecalculateController recalculateController
    ) {
        this.router = router;
        this.settlementController = settlementController;
        this.healthController = healthController;
        this.recalculateController = recalculateController;
    }

    public void setupRoutes() {
        log.info("Setting up API routes...");

        // CORS headers - add to all routes
        router.route().handler(ctx -> {
            ctx.response()
                    .putHeader("Access-Control-Allow-Origin", "*")
                    .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, Accept")
                    .putHeader("Access-Control-Allow-Credentials", "true")
                    .putHeader("Access-Control-Max-Age", "86400"); // 24 hours
            ctx.next();
        });

        // Handle OPTIONS preflight requests for all routes
        router.options("/*").handler(ctx -> {
            ctx.response().setStatusCode(204).end();
        });

        // Global handlers
        router.route().handler(LoggerHandler.create());
        router.route().handler(BodyHandler.create());

        // Health check
        router.get("/health").handler(healthController);

        // Settlement ingestion
        router.post("/api/settlements").handler(settlementController);

        // Manual recalculation
        router.post("/api/recalculate").handler(recalculateController);

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

        log.info("API routes configured successfully");
    }

    public Router getRouter() {
        return router;
    }
}
