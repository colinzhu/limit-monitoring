package com.tvpc.adapter.in.web;

import com.tvpc.adapter.in.web.ingestion.SettlementIngestionHandler;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import lombok.RequiredArgsConstructor;

/**
 * Router configuration for settlement endpoints
 */
@RequiredArgsConstructor
public class WebRouter {

    private final Router router;
    private final SettlementIngestionHandler settlementIngestionHandler;

    public void setupRoutes() {
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

        // API Test UI endpoint
        router.get("/api-test")
                .handler(ctx -> {
                    ctx.response()
                            .putHeader("Content-Type", "text/html; charset=utf-8")
                            .sendFile("static/api-test.html");
                });
    }
}
