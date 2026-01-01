package com.tvpc.domain.ports.outbound;

import com.tvpc.domain.Settlement;
import io.vertx.core.Future;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for settlement persistence operations.
 * Defines the contract for how the application layer interacts with settlement storage.
 */
public interface SettlementRepositoryPort {

    /**
     * Saves a settlement and returns its auto-generated sequence ID.
     *
     * @param settlement The settlement to save
     * @return The generated sequence ID
     */
    Future<Long> save(Settlement settlement);

    /**
     * Marks previous versions of a settlement as old.
     *
     * @param settlementId The settlement identifier
     * @param pts The trading system identifier
     * @param processingEntity The processing entity
     * @return Void future
     */
    Future<Void> markOldVersions(String settlementId, String pts, String processingEntity);

    /**
     * Finds the counterparty ID of the previous version of a settlement.
     *
     * @param settlementId The settlement identifier
     * @param pts The trading system identifier
     * @param processingEntity The processing entity
     * @param currentId The current settlement sequence ID
     * @return Optional containing previous counterparty ID, or empty if none
     */
    Future<Optional<String>> findPreviousCounterparty(String settlementId, String pts, String processingEntity, Long currentId);

    /**
     * Finds the latest version of a settlement.
     *
     * @param settlementId The settlement identifier
     * @param pts The trading system identifier
     * @param processingEntity The processing entity
     * @return Optional containing latest settlement, or empty if not found
     */
    Future<Optional<Settlement>> findLatestVersion(String settlementId, String pts, String processingEntity);

    /**
     * Finds all settlements for a group with filtering applied.
     * Returns only latest versions of PAY settlements with non-CANCELLED status.
     *
     * @param pts The trading system identifier
     * @param processingEntity The processing entity
     * @param counterpartyId The counterparty identifier
     * @param valueDate The value date (ISO format)
     * @param maxSeqId Maximum sequence ID to include
     * @return List of filtered settlements
     */
    Future<List<Settlement>> findByGroupWithFilters(
            String pts,
            String processingEntity,
            String counterpartyId,
            String valueDate,
            Long maxSeqId
    );
}
