package com.tvpc.application.port.inbound;

import com.tvpc.application.dto.SettlementRequest;
import io.vertx.core.Future;

/**
 * Inbound port - Use case for processing settlements
 * Primary port (driven by the presentation layer)
 */
public interface ProcessSettlementUseCase {
    /**
     * Process a settlement through the ingestion flow
     * @param request The settlement request DTO
     * @return Future with the generated sequence ID (REF_ID)
     */
    Future<Long> processSettlement(SettlementRequest request);
}
