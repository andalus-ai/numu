package tech.kayys.numou.planning.adapter.postgres;

/**
 * Transport-neutral boundary for publishing committed schedule-change events.
 *
 * <p>Implementations may publish to an in-process event bus, Kafka, AMQP, or another
 * transport. The outbox identifier is the stable event id and must be propagated to
 * consumers so duplicate delivery can be deduplicated. Implementations should return
 * only after the transport has accepted the message, not merely after buffering it
 * in process memory.
 */
@FunctionalInterface
public interface ScheduleChangeEventPublisher {
    void publish(JdbcScheduleChangeOutboxRelayStore.ClaimedEvent event) throws Exception;
}
