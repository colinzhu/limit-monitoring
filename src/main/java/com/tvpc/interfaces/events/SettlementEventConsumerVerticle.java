package com.tvpc.interfaces.events;

import com.tvpc.domain.ports.outbound.EventConsumerPort;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event consumer verticle.
 * Consumes settlement events from the event bus and processes them.
 * Wraps EventConsumerPort in a Vert.x verticle for deployment.
 */
public class SettlementEventConsumerVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(SettlementEventConsumerVerticle.class);

    private final EventConsumerPort eventConsumer;

    public SettlementEventConsumerVerticle(EventConsumerPort eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        log.info("Starting SettlementEventConsumerVerticle...");

        eventConsumer.start()
                .onSuccess(v -> {
                    log.info("Event consumer started successfully on address: {}",
                            eventConsumer.getEventAddress());
                    startPromise.complete();
                })
                .onFailure(error -> {
                    log.error("Failed to start event consumer verticle", error);
                    startPromise.fail(error);
                });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        log.info("Stopping SettlementEventConsumerVerticle...");

        eventConsumer.stop()
                .onSuccess(v -> {
                    log.info("Event consumer stopped successfully");
                    stopPromise.complete();
                })
                .onFailure(error -> {
                    log.error("Failed to stop event consumer verticle", error);
                    stopPromise.fail(error);
                });
    }
}
