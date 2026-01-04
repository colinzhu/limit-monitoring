package com.tvpc.presentation.controller;

import com.tvpc.application.port.inbound.RecalculateUseCase;
import com.tvpc.presentation.dto.RecalculateRequest;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

/**
 * HTTP Controller for manual recalculation
 * Presentation layer - Primary adapter
 */
public class RecalculateController implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(RecalculateController.class);

    private final RecalculateUseCase recalculateUseCase;

    public RecalculateController(RecalculateUseCase recalculateUseCase) {
        this.recalculateUseCase = recalculateUseCase;
    }

    @Override
    public void handle(RoutingContext context) {
        JsonObject requestBody = context.body().asJsonObject();

        if (requestBody == null) {
            sendError(context, 400, "Request body is required");
            return;
        }

        try {
            RecalculateRequest request = requestBody.mapTo(RecalculateRequest.class);

            // TODO: Get user ID from authentication context
            String userId = "system-admin"; // Placeholder

            recalculateUseCase.recalculate(
                    request.getPts(),
                    request.getProcessingEntity(),
                    request.getCounterpartyId(),
                    request.getValueDateFrom() != null ? LocalDate.parse(request.getValueDateFrom()) : null,
                    request.getValueDateTo() != null ? LocalDate.parse(request.getValueDateTo()) : null,
                    userId,
                    request.getReason()
            )
            .onSuccess(v -> {
                JsonObject response = new JsonObject()
                        .put("status", "COMPLETED")
                        .put("message", "Recalculation completed");

                context.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(response.encode());
            })
            .onFailure(error -> {
                log.error("Recalculation failed", error);
                sendError(context, 500, "Recalculation failed: " + error.getMessage());
            });

        } catch (Exception e) {
            log.error("Error parsing request", e);
            sendError(context, 400, "Invalid request format: " + e.getMessage());
        }
    }

    private void sendError(RoutingContext context, int statusCode, String message) {
        JsonObject response = new JsonObject()
                .put("status", "error")
                .put("message", message);

        context.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(response.encode());
    }
}
