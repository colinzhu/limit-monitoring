package com.tvpc.infrastructure.messaging;

import com.tvpc.domain.SettlementEvent;
import com.tvpc.domain.ports.inbound.RunningTotalProcessorUseCase;
import com.tvpc.domain.ports.outbound.EventConsumerPort;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vert.x implementation of EventConsumerPort.
 * Consumes settlement events from the event bus and processes them.
 * Runs as a single-threaded consumer to eliminate race conditions.
 */
public class VertxEventConsumer implements EventConsumerPort {

    private static final Logger logger = LoggerFactory.getLogger(VertxEventConsumer.class);

    private final Vertx vertx;
    private final RunningTotalProcessorUseCase processorUseCase;

    public VertxEventConsumer(Vertx vertx, RunningTotalProcessorUseCase processorUseCase) {
        this.vertx = vertx;
        this.processorUseCase = processorUseCase;
    }

    @Override
    public Future<Void> start() {
        Promise<Void> promise = Promise.promise();

        try {
            // Register consumer on event bus
            vertx.eventBus().consumer(VertxEventPublisher.SETTLEMENT_EVENT_ADDRESS, this::handleEvent)
                    .completionHandler(result -> {
                        if (result.succeeded()) {
                            logger.info("Event consumer started successfully on address: {}",
                                    VertxEventPublisher.SETTLEMENT_EVENT_ADDRESS);
                            promise.complete();
                        } else {
                            logger.error("Failed to start event consumer", result.cause());
                            promise.fail(result.cause());
                        }
                    });

        } catch (Exception e) {
            logger.error("Exception starting event consumer", e);
            promise.fail(e);
        }

        return promise.future();
    }

    @Override
    public Future<Void> stop() {
        Promise<Void> promise = Promise.promise();
        logger.info("Stopping event consumer");
        promise.complete();
        return promise.future();
    }

    /**
     * Handle incoming settlement event from event bus
     */
    private void handleEvent(Message<SettlementEvent> message) {
        SettlementEvent event = message.body();

        logger.info("Received settlement event: {}", event);

        // Process the event using the use case
        processorUseCase.processEvent(event)
                .onSuccess(v -> {
                    logger.info("Successfully processed event: {}", event);
                    // Acknowledge the message
                    message.reply("OK");
                })
                .onFailure(err -> {
                    logger.error("Failed to process event: {}", event, err);
                    // Reply with error
                    message.fail(500, err.getMessage());
                });
    }

    /**
     * Get the event bus address this consumer listens to
     */
    @Override
    public String getEventAddress() {
        return VertxEventPublisher.SETTLEMENT_EVENT_ADDRESS;
    }
}
