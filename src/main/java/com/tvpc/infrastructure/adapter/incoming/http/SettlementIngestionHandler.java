package com.tvpc.infrastructure.adapter.incoming.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tvpc.application.dto.SettlementRequest;
import com.tvpc.application.dto.SettlementResponse;
import com.tvpc.application.port.incoming.SettlementIngestionUseCase;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for settlement ingestion
 * Handles POST /api/settlements
 */
public class SettlementIngestionHandler implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(SettlementIngestionHandler.class);

    private final SettlementIngestionUseCase ingestionUseCase;
    private final ObjectMapper objectMapper;

    public SettlementIngestionHandler(SettlementIngestionUseCase ingestionUseCase) {
        this.ingestionUseCase = ingestionUseCase;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void handle(RoutingContext context) {
        log.info("Handler called, getting request body...");

        // Get request body
        RequestBody body = context.body();
        log.info("RequestBody object: {}", body);

        JsonObject requestBody = body.asJsonObject();
        log.info("Parsed JSON body: {}", requestBody);

        if (requestBody == null) {
            log.warn("Request body is null");
            sendError(context, 400, "Request body is required");
            return;
        }

        try {
            // Convert JSON to DTO
            SettlementRequest request = requestBody.mapTo(SettlementRequest.class);

            log.info("Received settlement ingestion request: {}", request.getSettlementId());

            // Process the settlement
            ingestionUseCase.processSettlement(request)
                    .onSuccess(response -> {
                        int statusCode = response.getStatus().equals("success") ? 201 : 500;

                        log.info("Settlement {} processed with status: {}",
                                request.getSettlementId(), response.getStatus());

                        context.response()
                                .setStatusCode(statusCode)
                                .putHeader("Content-Type", "application/json")
                                .end(JsonObject.mapFrom(response).encode());
                    })
                    .onFailure(error -> {
                        log.error("Failed to process settlement {}: {}",
                                request.getSettlementId(), error.getMessage(), error);

                        SettlementResponse response = SettlementResponse.error(error.getMessage());
                        context.response()
                                .setStatusCode(500)
                                .putHeader("Content-Type", "application/json")
                                .end(JsonObject.mapFrom(response).encode());
                    });

        } catch (Exception e) {
            log.error("Error parsing request body", e);
            sendError(context, 400, "Invalid request format: " + e.getMessage());
        }
    }

    private void sendError(RoutingContext context, int statusCode, String message) {
        SettlementResponse response = SettlementResponse.error(message);
        context.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject.mapFrom(response).encode());
    }
}
