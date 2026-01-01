package com.tvpc.domain.ports.inbound;

import com.tvpc.dto.SettlementRequest;
import io.vertx.core.Future;

/**
 * Inbound port for settlement ingestion use case.
 * Defines the contract for processing settlement requests.
 */
public interface SettlementIngestionUseCase {

    /**
     * Processes a settlement through the complete ingestion flow.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate settlement data</li>
     *   <li>Save settlement and get sequence ID</li>
     *   <li>Mark old versions</li>
     *   <li>Detect counterparty changes</li>
     *   <li>Generate events</li>
     *   <li>Calculate running total</li>
     * </ol>
     *
     * @param request The settlement request DTO
     * @return The generated sequence ID
     * @throws IllegalArgumentException if validation fails
     */
    Future<Long> processSettlement(SettlementRequest request);
}
