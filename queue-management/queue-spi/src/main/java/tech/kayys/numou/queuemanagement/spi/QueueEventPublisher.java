package tech.kayys.numou.queuemanagement.spi;

/** Transport-neutral publisher. Implementations must preserve eventId for consumer deduplication. */
@FunctionalInterface
public interface QueueEventPublisher {
    void publish(QueueOutboxMessage event) throws Exception;
}
