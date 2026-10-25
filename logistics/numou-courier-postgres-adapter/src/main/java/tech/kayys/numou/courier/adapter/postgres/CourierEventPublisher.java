package tech.kayys.numou.courier.adapter.postgres;

/**
 * Deployment-provided transport adapter (for example Kafka, AMQP, or an HTTP event gateway).
 * Implementations must use tenantId + eventId as the stable idempotency key because delivery is at-least-once.
 */
public interface CourierEventPublisher {
    /** Return only after the destination has accepted the event; throw on a retryable or permanent failure. */
    void publish(String destination, CourierPublishedEvent event) throws Exception;
}
