package com.tvpc.application.port.outbound;

import io.vertx.core.Future;

/**
 * Outbound port - Repository for notifications
 * Secondary port (driven by the infrastructure layer)
 */
public interface NotificationRepository {
    /**
     * Send notification to external system
     * @param settlementId Business settlement ID
     * @param status Status (e.g., AUTHORISED)
     * @param details Additional details
     * @return Future indicating success/failure
     */
    Future<Void> sendNotification(String settlementId, String status, String details);

    /**
     * Queue notification for retry
     * @param settlementId Business settlement ID
     * @param status Status
     * @param details Additional details
     * @param retryCount Current retry count
     * @return Future indicating completion
     */
    Future<Void> queueNotification(String settlementId, String status, String details, int retryCount);
}
