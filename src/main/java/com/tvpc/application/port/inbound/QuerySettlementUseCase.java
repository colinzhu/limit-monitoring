package com.tvpc.application.port.inbound;

import com.tvpc.application.dto.SettlementResponse;
import io.vertx.core.Future;

import java.time.LocalDate;
import java.util.List;

/**
 * Inbound port - Use case for querying settlements
 * Primary port (driven by the presentation layer)
 */
public interface QuerySettlementUseCase {
    /**
     * Query settlement by settlement ID
     * @param settlementId The business settlement ID
     * @return Future with settlement response including calculated status
     */
    Future<SettlementResponse> queryBySettlementId(String settlementId);

    /**
     * Search settlements by criteria
     * @param pts Primary Trading System (optional)
     * @param processingEntity Business unit (optional)
     * @param counterpartyId Counterparty (optional)
     * @param valueDateFrom Start date (optional)
     * @param valueDateTo End date (optional)
     * @param direction PAY/RECEIVE (optional)
     * @param businessStatus Status filter (optional)
     * @return Future with list of settlement responses
     */
    Future<List<SettlementResponse>> search(
            String pts,
            String processingEntity,
            String counterpartyId,
            LocalDate valueDateFrom,
            LocalDate valueDateTo,
            String direction,
            String businessStatus
    );
}
