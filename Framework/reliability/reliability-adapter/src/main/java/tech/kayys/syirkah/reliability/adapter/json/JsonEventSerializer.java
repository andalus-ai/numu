package tech.kayys.syirkah.reliability.adapter.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import tech.kayys.syirkah.event.domain.BusinessEvent;
import tech.kayys.syirkah.event.domain.EventEnvelope;
import tech.kayys.syirkah.event.domain.identifier.CausationId;
import tech.kayys.syirkah.event.domain.identifier.CorrelationId;
import tech.kayys.syirkah.event.domain.identifier.EventId;
import tech.kayys.syirkah.event.domain.valueobject.EventMetadata;
import tech.kayys.syirkah.event.domain.valueobject.EventVersion;
import tech.kayys.syirkah.event.spi.port.EventSerializerPort;

import java.util.Objects;
import java.util.UUID;

/**
 * Wire format v1 for {@link EventEnvelope}: a flat JSON object with a nested {@code payload}.
 *
 * <p>The envelope is written field by field instead of letting Jackson reflect over the
 * Java records, so the wire contract is explicit and does not change when a record is
 * refactored. Payload classes must be Jackson-serialisable records (primitives, strings,
 * UUIDs, java.time with the JavaTimeModule registered on the supplied mapper).
 *
 * <p>This replaces storing {@code event.toString()} as the payload, which is neither
 * parseable nor length-safe.
 */
public final class JsonEventSerializer implements EventSerializerPort {

    public static final String SPEC_VERSION = "1";

    private final ObjectMapper mapper;
    private final EventTypeRegistry registry;

    public JsonEventSerializer(ObjectMapper mapper, EventTypeRegistry registry) {
        this.mapper = Objects.requireNonNull(mapper, "mapper cannot be null");
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
    }

    @Override
    public String serialize(EventEnvelope<? extends BusinessEvent> envelope) {
        final var m = envelope.metadata();
        final ObjectNode root = mapper.createObjectNode();
        root.put("specVersion", SPEC_VERSION);
        root.put("eventId", m.eventId().value().toString());
        root.put("eventType", envelope.eventType());
        root.put("eventVersion", envelope.envelopeVersion().toString());
        root.put("correlationId", m.correlationId() == null ? null : m.correlationId().value());
        root.put("causationId", m.causationId() == null ? null : m.causationId().value());
        root.put("tenantId", m.tenantId() == null ? null : m.tenantId().toString());
        root.put("participantId", m.participantId() == null ? null : m.participantId().toString());
        root.put("actorId", m.actorId());
        root.put("occurredAt", m.occurredAt());
        root.put("traceId", m.traceId());
        root.set("payload", mapper.valueToTree(envelope.payload()));
        try {
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise event " + envelope.eventType(), e);
        }
    }

    @Override
    public EventEnvelope<? extends BusinessEvent> deserialize(String json, String eventType) {
        try {
            final JsonNode root = mapper.readTree(json);
            if (!SPEC_VERSION.equals(text(root, "specVersion"))) {
                throw new IllegalArgumentException("Unsupported envelope specVersion: " + text(root, "specVersion"));
            }
            final String declaredType = text(root, "eventType");
            if (eventType != null && !eventType.equals(declaredType)) {
                throw new IllegalArgumentException("Event type mismatch: expected " + eventType + " but was " + declaredType);
            }
            final var payloadClass = registry.classFor(declaredType);
            final BusinessEvent payload = mapper.treeToValue(root.get("payload"), payloadClass);
            final var meta = new EventMetadata(
                    EventId.fromString(text(root, "eventId")),
                    optional(root, "correlationId") == null ? null : CorrelationId.of(text(root, "correlationId")),
                    optional(root, "causationId") == null ? null : CausationId.of(text(root, "causationId")),
                    uuid(root, "tenantId"),
                    uuid(root, "participantId"),
                    optional(root, "actorId"),
                    optional(root, "occurredAt"),
                    optional(root, "traceId"));
            final var version = parseVersion(text(root, "eventVersion"));
            return new EventEnvelope<>(meta, version, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed event JSON", e);
        }
    }

    private static EventVersion parseVersion(String v) {
        final var parts = v.split("\\.");
        return EventVersion.of(Integer.parseInt(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 0);
    }

    private static String text(JsonNode root, String field) {
        final var s = optional(root, field);
        if (s == null) {
            throw new IllegalArgumentException("Missing field: " + field);
        }
        return s;
    }

    private static String optional(JsonNode root, String field) {
        final var n = root.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static UUID uuid(JsonNode root, String field) {
        final var s = optional(root, field);
        return s == null ? null : UUID.fromString(s);
    }
}
