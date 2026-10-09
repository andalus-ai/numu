package tech.kayys.syirkah.reliability.adapter.scheduler;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;
import tech.kayys.syirkah.event.domain.BusinessEvent;
import tech.kayys.syirkah.event.domain.EventEnvelope;
import tech.kayys.syirkah.event.spi.port.EventPublisherPort;
import tech.kayys.syirkah.event.spi.port.EventSerializerPort;

import java.util.List;

/**
 * Publishes envelopes to the {@code domain-events} channel (map it to a Kafka topic in application.properties).
 * The record key is the tenant id so a tenant's events stay ordered within a partition.
 * The returned Uni completes only after the broker acknowledges (configure {@code acks=all},
 * {@code enable.idempotence=true}). NOT COMPILED in sandbox.
 */
@ApplicationScoped
public class KafkaEventPublisher implements EventPublisherPort {

    @Inject
    @Channel("domain-events")
    MutinyEmitter<String> emitter;
    @Inject
    EventSerializerPort serializer;

    @Override
    public Uni<Void> publish(EventEnvelope<? extends BusinessEvent> envelope) {
        final var meta = envelope.metadata();
        final String key = meta.tenantId() != null ? meta.tenantId().toString() : meta.eventId().value().toString();
        final var kafka = OutgoingKafkaRecordMetadata.<String>builder()
                .withKey(key)
                .withHeaders(new org.apache.kafka.common.header.internals.RecordHeaders()
                        .add("event-type", envelope.eventType().getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .add("event-id", meta.eventId().value().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build();
        return emitter.sendMessage(Message.of(serializer.serialize(envelope)).addMetadata(kafka));
    }

    @Override
    public Uni<Void> publishAll(List<EventEnvelope<? extends BusinessEvent>> envelopes) {
        Uni<Void> chain = Uni.createFrom().voidItem();
        for (final var e : envelopes) {
            chain = chain.flatMap(ignored -> publish(e));
        }
        return chain;
    }
}
