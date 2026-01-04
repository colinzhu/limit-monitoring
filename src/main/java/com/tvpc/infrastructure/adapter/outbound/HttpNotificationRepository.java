package com.tvpc.infrastructure.adapter.outbound;

import com.tvpc.application.port.outbound.NotificationRepository;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP implementation of NotificationRepository
 * Infrastructure layer adapter
 *
 * NOTE: This is a skeleton implementation.
 * In production, this would use WebClient to send HTTP notifications.
 * For now, it just logs the notification attempts.
 */
public class HttpNotificationRepository implements NotificationRepository {
    private static final Logger log = LoggerFactory.getLogger(HttpNotificationRepository.class);

    private final String externalNotificationUrl;

    public HttpNotificationRepository(Object vertx, String externalNotificationUrl) {
        // vertx parameter kept for future use with WebClient
        this.externalNotificationUrl = externalNotificationUrl;
    }

    @Override
    public Future<Void> sendNotification(String settlementId, String status, String details) {
        if (externalNotificationUrl == null || externalNotificationUrl.isEmpty()) {
            log.warn("External notification URL not configured, skipping notification for {}", settlementId);
            return Future.succeededFuture();
        }

        log.info("WOULD SEND notification to {}: settlementId={}, status={}, details={}",
                externalNotificationUrl, settlementId, status, details);

        // TODO: Implement actual HTTP call using WebClient
        // For now, just log and succeed
        return Future.succeededFuture();
    }

    @Override
    public Future<Void> queueNotification(String settlementId, String status, String details, int retryCount) {
        // In production, this would save to NOTIFICATION_QUEUE table
        log.warn("WOULD QUEUE notification for retry: settlementId={}, status={}, retryCount={}",
                settlementId, status, retryCount);
        return Future.succeededFuture();
    }
}
