package com.tvpc.domain.port.repository;

import com.tvpc.domain.model.Settlement;
import io.vertx.core.Future;
import io.vertx.sqlclient.SqlConnection;

import java.util.Optional;

/**
 * Repository port for Settlement entity - handles CRUD operations for settlements
 * This is a domain port (interface) that defines the contract for settlement persistence.
 * Infrastructure layer will provide the implementation.
 */
public interface SettlementRepository {

    /**
     * Save a new settlement and return the auto-generated sequence ID
     * @param settlement The settlement to save
     * @param connection Database connection (for transaction support)
     * @return Future with the generated sequence ID
     */
    Future<Long> save(Settlement settlement, SqlConnection connection);

    /**
     * Mark old versions of a settlement as IS_OLD = true
     * @param settlementId Business settlement identifier
     * @param pts Primary Trading System
     * @param processingEntity Business unit
     * @param connection Database connection
     * @return Future indicating completion
     */
    Future<Void> markOldVersions(String settlementId, String pts, String processingEntity, SqlConnection connection);

    /**
     * Find the counterparty from the previous version of a settlement
     * @param settlementId Business settlement identifier
     * @param pts Primary Trading System
     * @param processingEntity Business unit
     * @param currentSeqId Current settlement's sequence ID
     * @param connection Database connection
     * @return Future with optional counterparty ID
     */
    Future<Optional<String>> findPreviousCounterparty(
            String settlementId,
            String pts,
            String processingEntity,
            Long currentSeqId,
            SqlConnection connection
    );
}

