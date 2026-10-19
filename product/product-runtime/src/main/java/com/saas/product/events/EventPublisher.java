package com.saas.product.events;

/**
 * Decoupled event publishing contract.
 *
 * Implementations:
 *  - CdiEventPublisher       → in-process CDI event bus (default, synchronous)
 *  - KafkaEventPublisher     → Smallrye Reactive Messaging / Kafka (async, cross-service)
 *  - OutboxEventPublisher    → transactional outbox pattern (guaranteed delivery)
 *  - CompositeEventPublisher → fan-out to multiple publishers
 *
 * All implementations MUST be non-blocking or use virtual threads.
 */
public interface EventPublisher {

    /**
     * Publish a domain event.
     * Must not throw — implementations should log and handle failures internally.
     */
    void publish(ProductEvent event);

    /**
     * Publish multiple events atomically (same transaction/batch).
     */
    default void publishAll(Iterable<ProductEvent> events) {
        events.forEach(this::publish);
    }
}
