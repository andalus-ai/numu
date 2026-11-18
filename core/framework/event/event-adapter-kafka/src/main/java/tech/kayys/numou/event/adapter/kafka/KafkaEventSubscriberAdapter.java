package tech.kayys.numou.event.adapter.kafka;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.spi.port.EventSubscriberPort;
import tech.kayys.numou.event.spi.port.EventSerializerPort;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * Kafka-backed inbound implementation of the shared event subscriber port.
 *
 * <p>Handlers are invoked sequentially for each event. The Kafka record is
 * acknowledged only after all matching handlers complete successfully.
 * A failed handler nacks the record so the connector's retry/failure strategy
 * can decide how it is redelivered or routed to a dead-letter topic.
 *
 * <p>Applications must make handlers idempotent. This adapter deliberately
 * does not own a business inbox or transaction boundary.
 */
@ApplicationScoped
public class KafkaEventSubscriberAdapter implements EventSubscriberPort {

    private static final String CHANNEL = "numou-events-in";
    private static final String EVENT_TYPE_HEADER = "numou-event-type";

    private final EventSerializerPort serializer;
    private final Map<String, CopyOnWriteArrayList<RegisteredHandler>> handlers =
            new ConcurrentHashMap<>();

    @Inject
    public KafkaEventSubscriberAdapter(EventSerializerPort serializer) {
        this.serializer = Objects.requireNonNull(serializer, "Event serializer is required");
    }

    @Override
    public SubscriptionHandle subscribe(
            String eventType,
            Function<EventEnvelope<? extends BusinessEvent>, Uni<Void>> handler) {
        String normalizedType = requireEventType(eventType);
        Objects.requireNonNull(handler, "Event handler is required");

        RegisteredHandler registered = new RegisteredHandler(UUID.randomUUID().toString(), normalizedType, handler);
        handlers.computeIfAbsent(normalizedType, ignored -> new CopyOnWriteArrayList<>())
                .add(registered);
        return registered;
    }

    @Override
    public void unsubscribe(SubscriptionHandle handle) {
        if (!(handle instanceof RegisteredHandler registered)) {
            throw new IllegalArgumentException("Subscription handle was not created by this adapter");
        }
        handlers.computeIfPresent(registered.eventType,
                (eventType, registrations) -> {
                    registrations.removeIf(candidate -> candidate.id.equals(registered.id));
                    return registrations.isEmpty() ? null : registrations;
                });
    }

    @Incoming(CHANNEL)
    @Acknowledgment(Acknowledgment.Strategy.MANUAL)
    public Uni<Void> consume(Message<String> message) {
        Objects.requireNonNull(message, "Kafka message is required");
        Uni<Void> processing = Uni.createFrom().item(() -> {
                    String eventType = eventTypeHeader(message);
                    String payload = message.getPayload();
                    if (payload == null || payload.isBlank()) {
                        throw new IllegalArgumentException("Kafka event payload is empty");
                    }
                    return serializer.deserialize(payload, eventType);
                })
                .chain(this::dispatch);

        // A successful nack is itself the terminal disposition of this record:
        // the connector's configured failure strategy (retry/DLQ) has handled it.
        // Complete the mediator invocation afterwards so one bad record does not
        // terminate the incoming channel and strand later records in the partition.
        // Keep ack outside the recovery branch so an ack failure is never followed
        // by a second terminal action (nack) on the same message.
        return processing
                .replaceWith(true)
                .onFailure().recoverWithUni(failure ->
                        Uni.createFrom().completionStage(() -> message.nack(failure))
                                .replaceWith(false))
                .chain(shouldAcknowledge -> shouldAcknowledge
                        ? Uni.createFrom().completionStage(message::ack)
                        : Uni.createFrom().voidItem());
    }

    private Uni<Void> dispatch(EventEnvelope<? extends BusinessEvent> envelope) {
        List<RegisteredHandler> registered = handlers.get(envelope.eventType());
        List<RegisteredHandler> matching = registered == null ? List.of() : List.copyOf(registered);
        if (matching.isEmpty()) {
            return Uni.createFrom().failure(new IllegalStateException(
                    "No event handler is registered for " + envelope.eventType()));
        }

        Uni<Void> result = Uni.createFrom().voidItem();
        for (RegisteredHandler registeredHandler : matching) {
            result = result.chain(() -> {
                Uni<Void> handled = registeredHandler.handler.apply(envelope);
                return handled == null
                        ? Uni.createFrom().failure(new IllegalStateException(
                                "Event handler returned null for " + envelope.eventType()))
                        : handled;
            });
        }
        return result;
    }

    private static String eventTypeHeader(Message<String> message) {
        IncomingKafkaRecordMetadata<?, ?> metadata = message.getMetadata(IncomingKafkaRecordMetadata.class)
                .orElseThrow(() -> new IllegalArgumentException("Kafka record metadata is missing"));
        Header header = metadata.getHeaders().lastHeader(EVENT_TYPE_HEADER);
        if (header == null) {
            throw new IllegalArgumentException("Kafka event type header is missing");
        }
        String eventType = new String(header.value(), StandardCharsets.UTF_8);
        return requireEventType(eventType);
    }

    private static String requireEventType(String eventType) {
        if (eventType == null || !eventType.matches("[A-Za-z0-9._-]+")
                || eventType.equals(".") || eventType.equals("..")) {
            throw new IllegalArgumentException("Event type is invalid");
        }
        return eventType;
    }

    private static final class RegisteredHandler implements SubscriptionHandle {
        private final String id;
        private final String eventType;
        private final Function<EventEnvelope<? extends BusinessEvent>, Uni<Void>> handler;

        private RegisteredHandler(
                String id,
                String eventType,
                Function<EventEnvelope<? extends BusinessEvent>, Uni<Void>> handler) {
            this.id = id;
            this.eventType = eventType;
            this.handler = handler;
        }

        @Override
        public String id() {
            return id;
        }
    }
}