package com.tvpc.application.port.outbound;

import com.tvpc.domain.model.Settlement;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlConnection;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port - Repository for Settlement entity
 * Secondary port (driven by the infrastructure layer)
 */
public interface SettlementRepository {
    /**
     * Save a settlement and return its sequence ID
     * Handles duplicates by returning existing ID
     * @param settlement The settlement to save
     * @param connection Database connection
     * @return Future with the sequence ID (REF_ID)
     */
    Future<Long> save(Settlement settlement, SqlConnection connection);

    /**
     * Mark old versions of a settlement
     * @param settlementId Business settlement ID
     * @param pts Primary Trading System
     * @param processingEntity Business unit
     * @param connection Database connection
     * @return Future indicating completion
     */
    Future<Void> markOldVersions(String settlementId, String pts, String processingEntity, SqlConnection connection);

    /**
     * Find the previous counterparty for a settlement
     * @param settlementId Business settlement ID
     * @param pts Primary Trading System
     * @param processingEntity Business unit
     * @param currentId Current sequence ID
     * @param connection Database connection
     * @return Future with optional previous counterparty ID
     */
    Future<Optional<String>> findPreviousCounterparty(String settlementId, String pts, String processingEntity, Long currentId, SqlConnection connection);

    /**
     * Find the latest version of a settlement
     * @param settlementId Business settlement ID
     * @param pts Primary Trading System
     * @param processingEntity Business unit
     * @return Future with optional settlement
     */
    Future<Optional<Settlement>> findLatestVersion(String settlementId, String pts, String processingEntity);

    /**
     * Find all settlements in a group up to a max sequence ID
     * @param pts Primary Trading System
     * @param processingEntity Business unit
     * @param counterpartyId Counterparty
     * @param valueDate Value date as string
     * @param maxSeqId Maximum sequence ID to include
     * @param connection Database connection
     * @return Future with list of settlements
     */
    Future<List<Settlement>> findByGroup(String pts, String processingEntity, String counterpartyId, String valueDate, Long maxSeqId, SqlConnection connection);

    /**
     * Find settlements in a group with filtering (PAY, not CANCELLED, latest version)
     * @param pts Primary Trading System
     * @param processingEntity Business unit
     * @param counterpartyId Counterparty
     * @param valueDate Value date as string
     * @param maxSeqId Maximum sequence ID to include
     * @param connection Database connection
     * @return Future with list of filtered settlements
     */
    Future<List<Settlement>> findByGroupWithFilters(String pts, String processingEntity, String counterpartyId, String valueDate, Long maxSeqId, SqlConnection connection);

    /**
     * Search settlements by criteria
     * @param pts Primary Trading System (optional)
     * @param processingEntity Business unit (optional)
     * @param counterpartyId Counterparty (optional)
     * @param valueDateFrom Start date (optional)
     * @param valueDateTo End date (optional)
     * @param direction Direction (optional)
     * @param businessStatus Status (optional)
     * @param limit Max results
     * @param offset Offset for pagination
     * @return Future with list of settlements
     */
    Future<List<Settlement>> search(
            String pts,
            String processingEntity,
            String counterpartyId,
            String valueDateFrom,
            String valueDateTo,
            String direction,
            String businessStatus,
            int limit,
            int offset
    );

    /**
     * Get distinct groups matching criteria
     * @param pts Primary Trading System (optional)
     * @param processingEntity Business unit (optional)
     * @param valueDateFrom Start date
     * @param valueDateTo End date
     * @return Future with list of group identifiers
     */
    Future<List<String>> getDistinctGroups(
            String pts,
            String processingEntity,
            String valueDateFrom,
            String valueDateTo
    );
}
