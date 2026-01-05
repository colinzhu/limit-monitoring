package com.tvpc.adapter.out.messaging;

import com.tvpc.application.port.out.DomainEventPublisher;
import com.tvpc.domain.event.SettlementEvent;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vert.x implementation of DomainEventPublisher
 * Infrastructure layer adapter
 */
public class VertxEventPublisher implements DomainEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(VertxEventPublisher.class);

    private final Vertx vertx;

    public VertxEventPublisher(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public void publish(SettlementEvent event) {
        log.debug("Publishing settlement event: {}", event.getGroupKey());

        // In the current implementation, events are processed synchronously
        // This adapter is kept for future use with event bus
        vertx.eventBus().publish("settlement.events", event);
    }
}
