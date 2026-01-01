package com.tvpc.interfaces.http;

import com.tvpc.domain.ports.outbound.ExchangeRateRepositoryPort;
import com.tvpc.domain.ports.outbound.SettlementRepositoryPort;
import com.tvpc.interfaces.http.handlers.SettlementIngestionHandler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Router configuration for settlement endpoints.
 * Maps HTTP routes to handlers.
 */
public class SettlementRouter {

    private static final Logger log = LoggerFactory.getLogger(SettlementRouter.class);

    private final Router router;
    private final SettlementIngestionHandler settlementIngestionHandler;
    private final SettlementRepositoryPort settlementRepository;
    private final ExchangeRateRepositoryPort exchangeRateRepository;

    public SettlementRouter(Router router, SettlementIngestionHandler settlementIngestionHandler,
                           SettlementRepositoryPort settlementRepository,
                           ExchangeRateRepositoryPort exchangeRateRepository) {
        this.router = router;
        this.settlementIngestionHandler = settlementIngestionHandler;
        this.settlementRepository = settlementRepository;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    public Router getRouter() {
        return router;
    }

    public void setupRoutes() {
        // CORS headers - add to all routes
        router.route().handler(ctx -> {
            ctx.response()
                    .putHeader("Access-Control-Allow-Origin", "*")
                    .putHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                    .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With")
                    .putHeader("Access-Control-Allow-Credentials", "true");
            ctx.next();
        });

        // Handle OPTIONS preflight requests
        router.options("/api/settlements").handler(ctx -> {
            ctx.response().setStatusCode(204).end();
        });

        // Settlement ingestion endpoint
        router.post("/api/settlements")
                .handler(BodyHandler.create())
                .handler(settlementIngestionHandler);

        // Health check endpoint
        router.get("/health")
                .handler(ctx -> {
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end("{\"status\":\"UP\",\"service\":\"payment-limit-monitoring\"}");
                });

        // Root endpoint
        router.get("/")
                .handler(ctx -> {
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end("{\"name\":\"Payment Limit Monitoring System\",\"version\":\"1.0.0\"}");
                });

        // Diagnostic endpoint - Get all exchange rates
        router.get("/diagnostic/exchange-rates")
                .handler(ctx -> {
                    exchangeRateRepository.getAllRates()
                            .onSuccess(rates -> {
                                JsonArray ratesArray = new JsonArray();
                                rates.forEach((currency, rate) -> {
                                    ratesArray.add(new JsonObject()
                                            .put("currency", currency)
                                            .put("rateToUSD", rate));
                                });
                                ctx.response()
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject()
                                                .put("status", "success")
                                                .put("exchangeRates", ratesArray)
                                                .encodePrettily());
                            })
                            .onFailure(error -> {
                                log.error("Failed to get exchange rates", error);
                                ctx.response()
                                        .setStatusCode(500)
                                        .putHeader("Content-Type", "application/json")
                                        .end(new JsonObject()
                                                .put("status", "error")
                                                .put("message", error.getMessage())
                                                .encode());
                            });
                });

        // Diagnostic endpoint - Get settlement currencies (summary)
        router.get("/diagnostic/settlements/currencies")
                .handler(ctx -> {
                    // This would need a new repository method, for now return info
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(new JsonObject()
                                    .put("status", "info")
                                    .put("message", "Check /diagnostic/exchange-rates to see available currency rates")
                                    .put("hint", "If you get 'No exchange rate found' error, add missing rates using: INSERT INTO EXCHANGE_RATE (CURRENCY, RATE_TO_USD) VALUES ('XXX', rate)")
                                    .encodePrettily());
                });
    }
}
