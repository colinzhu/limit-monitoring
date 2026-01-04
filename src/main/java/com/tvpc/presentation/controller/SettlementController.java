package com.tvpc.presentation.controller;

import com.tvpc.application.dto.SettlementRequest;
import com.tvpc.application.port.inbound.ProcessSettlementUseCase;
import com.tvpc.presentation.dto.ApiSettlementRequest;
import com.tvpc.presentation.dto.ApiSettlementResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Controller for settlement operations
 * Presentation layer - Primary adapter
 */
public class SettlementController implements Handler<RoutingContext> {
    private static final Logger log = LoggerFactory.getLogger(SettlementController.class);

    private final ProcessSettlementUseCase processSettlementUseCase;
    private final ObjectMapper objectMapper;

    public SettlementController(ProcessSettlementUseCase processSettlementUseCase) {
        this.processSettlementUseCase = processSettlementUseCase;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void handle(RoutingContext context) {
        log.info("Handler called, getting request body...");

        JsonObject requestBody = context.body().asJsonObject();
        log.info("Parsed JSON body: {}", requestBody);

        if (requestBody == null) {
            log.warn("Request body is null");
            sendError(context, 400, "Request body is required");
            return;
        }

        try {
            // Convert API DTO to Application DTO
            ApiSettlementRequest apiRequest = requestBody.mapTo(ApiSettlementRequest.class);
            SettlementRequest request = convertToApplicationDto(apiRequest);

            log.info("Received settlement ingestion request: {}", request.getSettlementId());

            // Process the settlement
            processSettlementUseCase.processSettlement(request)
                    .onSuccess(seqId -> {
                        ApiSettlementResponse response = ApiSettlementResponse.success(
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

                        int statusCode = 500;
                        if (error instanceof IllegalArgumentException) {
                            statusCode = 400;
                        }

                        ApiSettlementResponse response = ApiSettlementResponse.error(error.getMessage());
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

    private SettlementRequest convertToApplicationDto(ApiSettlementRequest apiRequest) {
        return SettlementRequest.builder()
                .settlementId(apiRequest.getSettlementId())
                .settlementVersion(apiRequest.getSettlementVersion())
                .pts(apiRequest.getPts())
                .processingEntity(apiRequest.getProcessingEntity())
                .counterpartyId(apiRequest.getCounterpartyId())
                .valueDate(apiRequest.getValueDate())
                .currency(apiRequest.getCurrency())
                .amount(apiRequest.getAmount())
                .businessStatus(apiRequest.getBusinessStatus())
                .direction(apiRequest.getDirection())
                .settlementType(apiRequest.getSettlementType())
                .build();
    }

    private void sendError(RoutingContext context, int statusCode, String message) {
        ApiSettlementResponse response = ApiSettlementResponse.error(message);
        context.response()
                .setStatusCode(statusCode)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject.mapFrom(response).encode());
    }
}
