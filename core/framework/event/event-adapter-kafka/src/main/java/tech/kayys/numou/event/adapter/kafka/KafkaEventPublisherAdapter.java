package tech.kayys.numou.event.adapter.kafka;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.spi.port.EventPublisherPort;
import tech.kayys.numou.event.spi.port.EventSerializerPort;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Kafka-backed implementation of the shared event publisher port.
 *
 * <p>The outgoing emitter completes only when the connector acknowledges the
 * record (or fails it). The outbox dispatcher therefore does not interpret
 * queue admission as successful broker delivery.
 *
 * <p>Kafka remains an optional infrastructure dependency: applications that
 * do not select this module do not pull the Kafka connector into their runtime.
 */
@ApplicationScoped
public class KafkaEventPublisherAdapter implements EventPublisherPort {

    private static final String CHANNEL = "numou-events";

    private final Function<Message<String>, Uni<Void>> sendMessage;
    private final EventSerializerPort serializer;
    private final String topicPrefix;

    @Inject
    public KafkaEventPublisherAdapter(
            @Channel(CHANNEL) MutinyEmitter<Message<String>> emitter,
            EventSerializerPort serializer,
            @ConfigProperty(name = "numou.events.kafka.topic-prefix", defaultValue = "numou.events.")
            String topicPrefix) {
        this(emitter::sendMessage, serializer, topicPrefix);
    }

    KafkaEventPublisherAdapter(
            Function<Message<String>, Uni<Void>> sendMessage,
            EventSerializerPort serializer,
            String topicPrefix) {
        this.sendMessage = Objects.requireNonNull(sendMessage, "Kafka sender is required");
        this.serializer = Objects.requireNonNull(serializer, "Event serializer is required");
        this.topicPrefix = requireTopicPrefix(topicPrefix);
    }

    @Override
    public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> envelope) {
        if (envelope == null || envelope.payload() == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("event envelope and payload are required"));
        }

        return Uni.createFrom().item(() -> {
                    String serialized = serializer.serialize(envelope);
                    if (serialized == null || serialized.isBlank()) {
                        throw new IllegalStateException("Event serializer returned an empty payload");
                    }
                    return serialized;
                })
                .chain(payload -> emitter.sendMessage(Message.of(payload).addMetadata(
                        kafkaMetadata(envelope))));
    }

    /**
     * Publishes sequentially to preserve the ordering guarantee of the shared
     * EventPublisherPort contract. A failed send stops the batch immediately.
     */
    @Override
    public Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> envelopes) {
        if (envelopes == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("event envelopes must not be null"));
        }

        Uni<Void> result = Uni.createFrom().voidItem();
        for (EventEnvelope<? extends BusinessEvent> envelope : envelopes) {
            result = result.chain(() -> publish(envelope));
        }
        return result;
    }

    private OutgoingKafkaRecordMetadata<String> kafkaMetadata(
            EventEnvelope<? extends BusinessEvent> envelope) {
        EventMetadata metadata = envelope.metadata();
        String eventType = requireEventType(envelope.eventType());
        String topic = topicPrefix + eventType;
        if (topic.length() > 249) {
            throw new IllegalArgumentException("Kafka topic name exceeds 249 characters");
        }

        String key = metadata.tenantId() != null
                ? metadata.tenantId().toString()
                : metadata.participantId() != null
                    ? metadata.participantId().toString()
                    : metadata.eventId().value().toString();

        Headers headers = new RecordHeaders();
        addHeader(headers, "numou-event-id", metadata.eventId().value().toString());
        addHeader(headers, "numou-event-type", eventType);
        addHeader(headers, "numou-event-version", envelope.envelopeVersion().toString());
        addHeader(headers, "numou-correlation-id",
                metadata.correlationId() == null ? null : metadata.correlationId().value());
        addHeader(headers, "numou-causation-id",
                metadata.causationId() == null ? null : metadata.causationId().value());
        addHeader(headers, "numou-tenant-id", uuid(metadata.tenantId()));
        addHeader(headers, "numou-participant-id", uuid(metadata.participantId()));
        addHeader(headers, "numou-actor-id", metadata.actorId());
        addHeader(headers, "numou-occurred-at", metadata.occurredAt());
        addHeader(headers, "traceparent", metadata.traceId());

        return OutgoingKafkaRecordMetadata.<String>builder()
                .withTopic(topic)
                .withKey(key)
                .withHeaders(headers)
                .build();
    }

    private static String requireEventType(String eventType) {
        if (eventType == null || !eventType.matches("[A-Za-z0-9._-]+")
                || eventType.equals(".") || eventType.equals("..")) {
            throw new IllegalArgumentException("Event type is not a valid Kafka topic suffix");
        }
        return eventType;
    }

    private static String requireTopicPrefix(String prefix) {
        Objects.requireNonNull(prefix, "Kafka topic prefix must not be null");
        if (prefix.isBlank() || !prefix.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Kafka topic prefix contains invalid characters");
        }
        return prefix;
    }

    private static String uuid(UUID value) {
        return value == null ? null : value.toString();
    }

    private static void addHeader(Headers headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
