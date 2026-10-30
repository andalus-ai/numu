package tech.kayys.numou.event.adapter.distributed;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.event.spi.port.EventSubscriberPort;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Compatibility placeholder for the former Kafka adapter.
 *
 * <p>This class deliberately fails closed. The previous implementation only
 * logged publication attempts and invoked process-local callbacks; it did not
 * connect to Kafka. Returning success caused durable outbox dispatchers to
 * acknowledge events that had never reached a broker.
 *
 * <p>Use a real, explicitly configured {@link EventPublisherPort} until the
 * dedicated Kafka adapter is available. This class remains temporarily to
 * avoid a source-incompatible removal while callers migrate.
 *
 * @deprecated This is not a transport implementation and must not be used.
 */
@Deprecated(forRemoval = true)
public final class DistributedKafkaEventPublisherAdapter
        implements EventPublisherPort, EventSubscriberPort {

    private static final String UNSUPPORTED =
            "Kafka transport is not implemented; refusing to acknowledge or subscribe to distributed events";

    public DistributedKafkaEventPublisherAdapter() {
        this("localhost:9092", "numou.events.");
    }

    public DistributedKafkaEventPublisherAdapter(String bootstrapServers, String defaultTopicPrefix) {
        Objects.requireNonNull(bootstrapServers, "bootstrapServers must not be null");
        Objects.requireNonNull(defaultTopicPrefix, "defaultTopicPrefix must not be null");
        if (bootstrapServers.isBlank() || defaultTopicPrefix.isBlank()) {
            throw new IllegalArgumentException("Kafka bootstrap servers and topic prefix must not be blank");
        }
    }

    @Override
    public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> envelope) {
        if (envelope == null || envelope.payload() == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("event envelope and payload are required"));
        }
        return Uni.createFrom().failure(new UnsupportedOperationException(UNSUPPORTED));
    }

    @Override
    public Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> envelopes) {
        if (envelopes == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("event envelopes must not be null"));
        }
        if (envelopes.isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        return Uni.createFrom().failure(new UnsupportedOperationException(UNSUPPORTED));
    }

    @Override
    public SubscriptionHandle subscribe(
            String eventType,
            Function<EventEnvelope<? extends BusinessEvent>, Uni<Void>> handler) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }

    @Override
    public void unsubscribe(SubscriptionHandle handle) {
        throw new UnsupportedOperationException(UNSUPPORTED);
    }
}
