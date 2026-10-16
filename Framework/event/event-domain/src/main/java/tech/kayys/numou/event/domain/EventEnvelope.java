package tech.kayys.numou.event.domain;

import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;

import java.util.Objects;

/**
 * Transport-neutral wrapper around a business event payload
 * (base01.md §P1-11).
 *
 * <p>Nothing in this type mentions a broker, a topic or a serialization
 * format. Kafka, RabbitMQ, HTTP webhooks and a database outbox all carry
 * exactly this envelope, which is what makes the transport replaceable.
 *
 * @param <T> the business event payload type
 */
public record EventEnvelope<T extends BusinessEvent>(
        EventMetadata metadata,
        EventVersion envelopeVersion,
        T payload) {

    public EventEnvelope {
        Objects.requireNonNull(metadata, "metadata cannot be null");
        Objects.requireNonNull(envelopeVersion, "envelopeVersion cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
    }

    /** Wraps a payload, taking the contract version from the payload itself. */
    public static <T extends BusinessEvent> EventEnvelope<T> of(EventMetadata metadata, T payload) {
        return new EventEnvelope<>(metadata, payload.eventVersion(), payload);
    }

    public String eventType() {
        return payload.eventType();
    }

    public boolean isTenantScoped() {
        return metadata.tenantId() != null;
    }
}
