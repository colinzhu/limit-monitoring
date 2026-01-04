package com.tvpc.domain.event;

/**
 * Port interface for publishing domain events
 * This is a secondary port (outbound) from the domain perspective
 */
public interface DomainEventPublisher {
    /**
     * Publish a settlement event
     * @param event The domain event to publish
     */
    void publish(SettlementEvent event);
}
