package com.tvpc.application.port.incoming;

import com.tvpc.application.dto.SettlementRequest;
import com.tvpc.application.dto.SettlementResponse;
import io.vertx.core.Future;

/**
 * Use case port for settlement ingestion
 * This is an application port (interface) that defines the contract for the settlement ingestion use case.
 * Incoming adapters (HTTP, messaging) will use this interface.
 */
public interface SettlementIngestionUseCase {

    /**
     * Process a settlement request through the 5-step ingestion flow
     * @param request The settlement request to process
     * @return Future with settlement response containing sequence ID or error
     */
    Future<SettlementResponse> processSettlement(SettlementRequest request);

    /**
     * Validate a settlement request without processing it
     * @param request The settlement request to validate
     * @return Future with settlement response indicating validation result
     */
    Future<SettlementResponse> validateSettlement(SettlementRequest request);
}
