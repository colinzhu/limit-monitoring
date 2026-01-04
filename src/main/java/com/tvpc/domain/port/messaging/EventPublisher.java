package com.tvpc.domain.port.messaging;

import com.tvpc.domain.model.SettlementEvent;
import io.vertx.core.Future;

/**
 * Messaging port for publishing settlement events
 * This is a domain port (interface) that defines the contract for event publishing.
 * Infrastructure layer will provide the implementation using Vert.x event bus or other messaging systems.
 */
public interface EventPublisher {

    /**
     * Publish a single settlement event
     * @param event The settlement event to publish
     * @return Future indicating completion
     */
    Future<Void> publish(SettlementEvent event);

    /**
     * Publish multiple settlement events
     * @param events List of settlement events to publish
     * @return Future indicating completion
     */
    Future<Void> publish(java.util.List<SettlementEvent> events);
}
