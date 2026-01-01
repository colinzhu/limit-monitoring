package com.tvpc.infrastructure.messaging;

import com.tvpc.domain.SettlementEvent;
import com.tvpc.domain.ports.outbound.EventPublisherPort;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.List;

/**
 * Vert.x implementation of EventPublisherPort.
 * Publishes settlement events to the Vert.x event bus.
 */
public class VertxEventPublisher implements EventPublisherPort {

    public static final String SETTLEMENT_EVENT_ADDRESS = "settlement.events";

    private final Vertx vertx;

    public VertxEventPublisher(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public Future<Void> publish(SettlementEvent event) {
        Promise<Void> promise = Promise.promise();

        try {
            // Publish to event bus
            vertx.eventBus().publish(SETTLEMENT_EVENT_ADDRESS, event);
            promise.complete();
        } catch (Exception e) {
            promise.fail(e);
        }

        return promise.future();
    }

    @Override
    public Future<Void> publishMultiple(List<SettlementEvent> events) {
        Promise<Void> promise = Promise.promise();

        try {
            // Publish all events to event bus
            for (SettlementEvent event : events) {
                vertx.eventBus().publish(SETTLEMENT_EVENT_ADDRESS, event);
            }
            promise.complete();
        } catch (Exception e) {
            promise.fail(e);
        }

        return promise.future();
    }
}
