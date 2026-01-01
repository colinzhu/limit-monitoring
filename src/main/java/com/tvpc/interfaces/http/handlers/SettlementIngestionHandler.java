package com.tvpc.interfaces.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tvpc.domain.ports.inbound.SettlementIngestionUseCase;
import com.tvpc.dto.SettlementRequest;
import com.tvpc.dto.SettlementResponse;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP handler for settlement ingestion.
 * Handles POST /api/settlements
 * Uses SettlementIngestionUseCase (ports only, no infrastructure dependencies)
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
        JsonObject requestBody = body.asJsonObject();

        if (requestBody == null) {
            log.warn("Request body is null");
            sendError(context, 400, "Request body is required");
            return;
        }

        try {
            // Convert JSON to DTO
            SettlementRequest request = requestBody.mapTo(SettlementRequest.class);

            log.info("Received settlement ingestion request: {}", request.getSettlementId());

            // Process the settlement using the use case
            ingestionUseCase.processSettlement(request)
                    .onSuccess(seqId -> {
                        SettlementResponse response = SettlementResponse.success(
                                "Settlement processed successfully",
                                seqId
                        );

                        log.info("Settlement {} processed successfully with seqId: {}",
                                request.getSettlementId(), seqId);

                        context.response()
                                .setStatusCode(201)
                                .putHeader("Content-Type", "application/json")
                                .end(JsonObject.mapFrom(response).encode());
                    })
                    .onFailure(error -> {
                        log.error("Failed to process settlement {}: {}",
                                request.getSettlementId(), error.getMessage(), error);

                        // Determine appropriate status code
                        int statusCode = 500;
                        String message = error.getMessage();

                        if (error instanceof IllegalArgumentException) {
                            statusCode = 400; // Validation error
                        }

                        SettlementResponse response = SettlementResponse.error(message);
                        context.response()
                                .setStatusCode(statusCode)
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
