package com.tvpc.presentation.controller;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Controller for health check
 * Presentation layer - Primary adapter
 */
public class HealthController implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    @Override
    public void handle(RoutingContext context) {
        log.debug("Health check requested");

        JsonObject response = new JsonObject()
                .put("status", "UP")
                .put("service", "payment-limit-monitoring");

        context.response()
                .setStatusCode(200)
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }
}
