package com.tvpc.domain.ports.inbound;

import com.tvpc.domain.SettlementEvent;
import io.vertx.core.Future;

/**
 * Inbound port for running total processing use case.
 * Handles event-driven calculation of running totals.
 */
public interface RunningTotalProcessorUseCase {

    /**
     * Processes a settlement event to calculate running total for a group.
     *
     * @param event The settlement event containing group information
     * @return Void future
     */
    Future<Void> processEvent(SettlementEvent event);
}
