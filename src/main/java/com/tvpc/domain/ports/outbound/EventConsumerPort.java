package com.tvpc.domain.ports.outbound;

import io.vertx.core.Future;

/**
 * Outbound port for event consumer operations.
 * Manages lifecycle of event bus consumers.
 */
public interface EventConsumerPort {

    /**
     * Starts the event consumer.
     *
     * @return Void future
     */
    Future<Void> start();

    /**
     * Stops the event consumer.
     *
     * @return Void future
     */
    Future<Void> stop();

    /**
     * Gets the event bus address this consumer listens to.
     *
     * @return The event address
     */
    String getEventAddress();
}
