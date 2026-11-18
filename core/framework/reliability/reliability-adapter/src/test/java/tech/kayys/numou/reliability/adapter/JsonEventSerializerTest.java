package tech.kayys.numou.reliability.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.reliability.adapter.json.EventTypeRegistry;
import tech.kayys.numou.reliability.adapter.json.JsonDomainEvent;
import tech.kayys.numou.reliability.adapter.json.JsonEventSerializer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonEventSerializerTest {

    public record TableSeated(UUID eventId, String occurredAtIso, UUID reservationId, int partySize) implements BusinessEvent {
        public java.time.Instant occurredAt() { return java.time.Instant.parse(occurredAtIso); }
        public String eventType() { return "restaurant.table.seated"; }
        public EventVersion eventVersion() { return EventVersion.of(1, 2); }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final EventTypeRegistry registry = new EventTypeRegistry().register("restaurant.table.seated", TableSeated.class);
    private final JsonEventSerializer serializer = new JsonEventSerializer(mapper, registry);

    private EventEnvelope<TableSeated> sample(UUID tenant) {
        final var meta = EventMetadata.of(tenant, null, "host-1", CorrelationId.of("corr-1"));
        return EventEnvelope.of(meta, new TableSeated(UUID.randomUUID(), "2026-10-07T12:00:00Z", UUID.randomUUID(), 4));
    }

    @Test
    void roundTripPreservesEveryField() {
        final var tenant = UUID.randomUUID();
        final var original = sample(tenant);
        final var json = serializer.serialize(original);
        final var back = serializer.deserialize(json, "restaurant.table.seated");

        assertThat(back.metadata().eventId()).isEqualTo(original.metadata().eventId());
        assertThat(back.metadata().tenantId()).isEqualTo(tenant);
        assertThat(back.metadata().correlationId().value()).isEqualTo("corr-1");
        assertThat(back.metadata().actorId()).isEqualTo("host-1");
        assertThat(back.envelopeVersion()).isEqualTo(EventVersion.of(1, 2));
        assertThat(back.payload()).isEqualTo(original.payload());
    }

    @Test
    void wireFormatIsStableAndExplicit() throws Exception {
        final var tree = mapper.readTree(serializer.serialize(sample(UUID.randomUUID())));
        assertThat(tree.fieldNames()).toIterable().contains(
                "specVersion", "eventId", "eventType", "eventVersion", "correlationId", "tenantId", "actorId", "payload");
        assertThat(tree.get("specVersion").asText()).isEqualTo("1");
        assertThat(tree.get("payload").get("partySize").asInt()).isEqualTo(4);
    }

    @Test
    void unknownEventTypeIsAPermanentError() {
        final var json = serializer.serialize(sample(UUID.randomUUID()));
        final var other = new JsonEventSerializer(mapper, new EventTypeRegistry());
        assertThatThrownBy(() -> other.deserialize(json, null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown event type");
    }

    @Test
    void typeMismatchAndMalformedInputAreRejected() {
        final var json = serializer.serialize(sample(UUID.randomUUID()));
        assertThatThrownBy(() -> serializer.deserialize(json, "other.type")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serializer.deserialize("{not json", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serializer.deserialize("{\"specVersion\":\"9\"}", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void platformEventWithoutTenantRoundTrips() {
        final var meta = EventMetadata.of(null, null, "system", CorrelationId.of("c"));
        final var env = EventEnvelope.of(meta, new TableSeated(UUID.randomUUID(), "2026-10-07T12:00:00Z", UUID.randomUUID(), 2));
        assertThat(serializer.deserialize(serializer.serialize(env), null).metadata().tenantId()).isNull();
    }


    @Test
    void organizationAssociationLifecycleEventUsesThePlatformGenericFallback() throws Exception {
        final UUID tenantId = UUID.randomUUID();
        final UUID eventId = UUID.randomUUID();
        final UUID associationId = UUID.randomUUID();
        final String eventType = "organization.party-association.changed";

        final var platformRegistry = new ReliabilityProducer().registry();
        assertThat(platformRegistry.classFor(eventType)).isEqualTo(JsonDomainEvent.class);

        final var data = mapper.createObjectNode();
        data.put("associationId", associationId.toString());
        data.put("organizationId", UUID.randomUUID().toString());
        data.put("partyId", UUID.randomUUID().toString());
        data.put("status", "ACTIVE");
        data.put("version", 1);
        data.put("transition", "PENDING_VERIFICATION_TO_ACTIVE");

        final var payload = new JsonDomainEvent(eventId, "2026-11-02T00:00:00Z", eventType, data);
        final var metadata = EventMetadata.of(tenantId, null, "verifier-1", CorrelationId.of("org-association-correlation"));
        final var envelope = EventEnvelope.of(metadata, payload);
        final var platformSerializer = new JsonEventSerializer(mapper, platformRegistry);

        final var restored = platformSerializer.deserialize(
                platformSerializer.serialize(envelope), eventType);
        assertThat(restored.metadata().tenantId()).isEqualTo(tenantId);
        assertThat(restored.metadata().actorId()).isEqualTo("verifier-1");
        assertThat(restored.payload()).isInstanceOf(JsonDomainEvent.class);
        final var restoredPayload = (JsonDomainEvent) restored.payload();
        assertThat(restoredPayload.eventId()).isEqualTo(eventId);
        assertThat(restoredPayload.data().path("associationId").asText()).isEqualTo(associationId.toString());
        assertThat(restoredPayload.data().path("transition").asText())
                .isEqualTo("PENDING_VERIFICATION_TO_ACTIVE");
    }

    @Test
    void registeringTwoClassesForOneTypeFails() {
        assertThatThrownBy(() -> registry.register("restaurant.table.seated", BusinessEvent.class))
                .isInstanceOf(IllegalStateException.class);
    }
}
