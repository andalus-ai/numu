package tech.kayys.numou.reservation.runtime.quarkus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import tech.kayys.numou.reservation.adapter.postgres.ReservationOutboxMessage;
import tech.kayys.numou.reservation.adapter.postgres.ReservationOutboxPublisher;

/**
 * Optional Kafka transport adapter for the reservation outbox.
 *
 * <p>The complete envelope is serialized as JSON so consumers receive the stable event ID,
 * tenant, aggregate metadata, event type, original payload, occurrence time, and attempt
 * number. Consumers should deduplicate on {@code eventId}; Kafka producer idempotence does
 * not make the PostgreSQL-to-Kafka boundary exactly-once.</p>
 */
@ApplicationScoped
public class KafkaReservationOutboxPublisher implements ReservationOutboxPublisher {
    private final Emitter<String> emitter;
    private final ObjectMapper mapper;

    @Inject
    public KafkaReservationOutboxPublisher(
            @Channel("reservation-outbox-events") Emitter<String> emitter,
            ObjectMapper mapper) {
        this.emitter = emitter;
        this.mapper = mapper;
    }

    @Override
    public Uni<Void> publish(ReservationOutboxMessage message) {
        final String envelope;
        try {
            envelope = mapper.writeValueAsString(message);
        } catch (JsonProcessingException failure) {
            return Uni.createFrom().failure(failure);
        }
        return Uni.createFrom().completionStage(() -> emitter.send(envelope));
    }
}
