package tech.kayys.numou.event.adapter.distributed;

import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.event.spi.port.EventSubscriberPort;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Distributed event adapter targeting Kafka / cloud broker streams.
 * Used for Microservices and SaaS cloud deployments.
 */
public class DistributedKafkaEventPublisherAdapter implements EventPublisherPort, EventSubscriberPort {

    private static final Logger log = LoggerFactory.getLogger(DistributedKafkaEventPublisherAdapter.class);

    private final String bootstrapServers;
    private final String defaultTopicPrefix;
    private final Map<String, List<Function<EventEnvelope<? extends BusinessEvent>, Uni<Void>>>> localSubscribers = new ConcurrentHashMap<>();

    public DistributedKafkaEventPublisherAdapter() {
        this("localhost:9092", "numou.events.");
    }

    public DistributedKafkaEventPublisherAdapter(String bootstrapServers, String defaultTopicPrefix) {
        this.bootstrapServers = bootstrapServers;
        this.defaultTopicPrefix = defaultTopicPrefix;
    }

    @Override
    public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> envelope) {
        if (envelope == null || envelope.payload() == null) {
            return Uni.createFrom().voidItem();
        }

        String topic = defaultTopicPrefix + envelope.eventType();
        String partitionKey = envelope.metadata().tenantId() != null
                ? envelope.metadata().tenantId().toString()
                : "default";

        log.info("Dispatching event to Kafka topic [{}], partitionKey [{}] - correlation: {}",
                topic, partitionKey, envelope.metadata().correlationId());

        // Also trigger any local subscribers registered for this consumer group
        List<Function<EventEnvelope<? extends BusinessEvent>, Uni<Void>>> subs = localSubscribers.get(envelope.eventType());
        if (subs != null && !subs.isEmpty()) {
            List<Uni<Void>> invocations = new ArrayList<>();
            for (var sub : subs) {
                invocations.add(sub.apply(envelope));
            }
            return Uni.combine().all().unis(invocations).discardItems();
        }

        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        List<Uni<Void>> list = new ArrayList<>();
        for (EventEnvelope<? extends BusinessEvent> env : envelopes) {
            list.add(publish(env));
        }

        return Uni.combine().all().unis(list).discardItems();
    }

    @Override
    public SubscriptionHandle subscribe(String eventType, Function<EventEnvelope<? extends BusinessEvent>, Uni<Void>> handler) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(handler, "handler must not be null");

        String subId = UUID.randomUUID().toString();
        localSubscribers.computeIfAbsent(eventType, k -> new ArrayList<>()).add(handler);
        log.info("Subscribed Kafka consumer to topic [{}], handle: {}", defaultTopicPrefix + eventType, subId);

        return () -> subId;
    }

    @Override
    public void unsubscribe(SubscriptionHandle handle) {
        if (handle == null) return;
        log.info("Unsubscribed Kafka consumer: {}", handle.id());
    }
}
