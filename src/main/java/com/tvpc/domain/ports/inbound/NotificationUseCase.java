package com.tvpc.domain.ports.inbound;

import io.vertx.core.Future;

/**
 * Inbound port for notification operations.
 */
public interface NotificationUseCase {

    /**
     * Sends notification to external system for authorized settlement.
     * Implements exponential backoff retry if external system is unavailable.
     *
     * @param settlementId The settlement identifier
     * @param settlementVersion The settlement version
     * @return Void future
     */
    Future<Void> notifyAuthorized(String settlementId, Long settlementVersion);

    /**
     * Processes pending notifications from queue.
     * Called by scheduled job.
     *
     * @return Void future
     */
    Future<Void> processPendingNotifications();
}
