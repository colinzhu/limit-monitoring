package com.tvpc.infrastructure.adapter.outgoing.messaging;

import com.tvpc.domain.model.SettlementEvent;
import com.tvpc.domain.port.messaging.EventPublisher;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Vert.x implementation of EventPublisher port
 * Publishes settlement events to Vert.x event bus
 */
public class VertxEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(VertxEventPublisher.class);

    public static final String SETTLEMENT_EVENT_ADDRESS = "settlement.events";

    private final Vertx vertx;

    public VertxEventPublisher(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public Future<Void> publish(SettlementEvent event) {
        log.debug("Publishing settlement event: {}", event);

        vertx.eventBus().publish(SETTLEMENT_EVENT_ADDRESS, event);
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> publish(List<SettlementEvent> events) {
        if (events.isEmpty()) {
            return Future.succeededFuture();
        }

        log.debug("Publishing {} settlement events", events.size());

        // Publish all events
        for (SettlementEvent event : events) {
            vertx.eventBus().publish(SETTLEMENT_EVENT_ADDRESS, event);
        }

        return Future.succeededFuture();
    }

    /**
     * Send event with confirmation (for critical operations)
     */
    public Future<String> sendWithConfirmation(SettlementEvent event) {
        log.debug("Sending settlement event with confirmation: {}", event);

        return vertx.eventBus()
                .request(SETTLEMENT_EVENT_ADDRESS, event)
                .map(message -> (String) message.body());
    }
}

