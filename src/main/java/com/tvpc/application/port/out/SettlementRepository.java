package com.tvpc.application.port.out;

import com.tvpc.domain.model.Settlement;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlConnection;

import java.util.List;
import java.util.Optional;

/**
 * Output port for settlement persistence operations
 */
public interface SettlementRepository {

    /**
     * Save a new settlement and return the auto-generated sequence ID
     */
    Future<Long> save(Settlement settlement, SqlConnection connection);

    /**
     * Mark old versions of a settlement as IS_OLD = true
     */
    Future<Void> markOldVersions(String settlementId, String pts, String processingEntity, SqlConnection connection);

    /**
     * Find the previous counterparty for a settlement (to detect changes)
     */
    Future<Optional<String>> findPreviousCounterparty(String settlementId, String pts, String processingEntity, Long currentId, SqlConnection connection);

    /**
     * Find the latest version of a settlement
     */
    Future<Optional<Settlement>> findLatestVersion(String settlementId, String pts, String processingEntity);

    /**
     * Find all settlements matching the group criteria
     */
    Future<List<Settlement>> findByGroup(String pts, String processingEntity, String counterpartyId, String valueDate, Long maxSeqId, SqlConnection connection);

    /**
     * Find settlements for a group with specific business status and direction filters
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
