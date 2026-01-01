package com.tvpc.domain.ports.outbound;

import com.tvpc.domain.SettlementEvent;
import io.vertx.core.Future;

import java.util.List;

/**
 * Outbound port for publishing domain events.
 */
public interface EventPublisherPort {

    /**
     * Publishes a single settlement event.
     *
     * @param event The settlement event to publish
     * @return Void future
     */
    Future<Void> publish(SettlementEvent event);

    /**
     * Publishes multiple settlement events.
     *
     * @param events List of settlement events
     * @return Void future
     */
    Future<Void> publishMultiple(List<SettlementEvent> events);
}
