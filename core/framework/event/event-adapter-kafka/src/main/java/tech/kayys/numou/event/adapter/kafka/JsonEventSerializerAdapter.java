package tech.kayys.numou.event.adapter.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tech.kayys.numou.event.domain.BusinessEvent;
import tech.kayys.numou.event.domain.EventEnvelope;
import tech.kayys.numou.event.domain.identifier.CausationId;
import tech.kayys.numou.event.domain.identifier.CorrelationId;
import tech.kayys.numou.event.domain.identifier.EventId;
import tech.kayys.numou.event.domain.valueobject.EventMetadata;
import tech.kayys.numou.event.domain.valueobject.EventVersion;
import tech.kayys.numou.event.spi.port.EventSerializerPort;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Version-1 JSON wire format for shared business events.
 *
 * <p>Payload classes are resolved only through the application-provided
 * {@link EventTypeRegistry}; wire data is never allowed to name a Java class.
 */
@ApplicationScoped
public class JsonEventSerializerAdapter implements EventSerializerPort {

    private static final int WIRE_FORMAT_VERSION = 1;

    private final ObjectMapper mapper;
    private final EventTypeRegistry registry;

    @Inject
    public JsonEventSerializerAdapter(ObjectMapper mapper, EventTypeRegistry registry) {
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper is required");
        this.registry = Objects.requireNonNull(registry, "Event type registry is required");
    }

    @Override
    public String serialize(EventEnvelope<? extends BusinessEvent> envelope) {
        Objects.requireNonNull(envelope, "Event envelope is required");
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("wireFormatVersion", WIRE_FORMAT_VERSION);
            root.put("eventType", envelope.eventType());
            ObjectNode version = root.putObject("envelopeVersion");
            version.put("major", envelope.envelopeVersion().major());
            version.put("minor", envelope.envelopeVersion().minor());

            EventMetadata metadata = envelope.metadata();
            ObjectNode meta = root.putObject("metadata");
            meta.put("eventId", metadata.eventId().value().toString());
            putNullable(meta, "correlationId",
                    metadata.correlationId() == null ? null : metadata.correlationId().value());
            putNullable(meta, "causationId",
                    metadata.causationId() == null ? null : metadata.causationId().value());
            putNullable(meta, "tenantId", string(metadata.tenantId()));
            putNullable(meta, "participantId", string(metadata.participantId()));
            putNullable(meta, "actorId", metadata.actorId());
            putNullable(meta, "occurredAt", metadata.occurredAt());
            putNullable(meta, "traceId", metadata.traceId());
            root.set("payload", mapper.valueToTree(envelope.payload()));
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize business event envelope", e);
        }
    }

    @Override
    public EventEnvelope<? extends BusinessEvent> deserialize(String wirePayload, String expectedEventType) {
        if (wirePayload == null || wirePayload.isBlank()) {
            throw new IllegalArgumentException("Event wire payload is required");
        }
        if (expectedEventType == null || expectedEventType.isBlank()) {
            throw new IllegalArgumentException("Expected event type is required");
        }

        try {
            JsonNode root = mapper.readTree(wirePayload);
            requireObject(root, "Event envelope");
            int wireVersion = requiredInt(root, "wireFormatVersion");
            if (wireVersion != WIRE_FORMAT_VERSION) {
                throw new IllegalArgumentException("Unsupported event wire format version: " + wireVersion);
            }

            String eventType = requiredText(root, "eventType");
            if (!expectedEventType.equals(eventType)) {
                throw new IllegalArgumentException("Event type header does not match envelope eventType");
            }
            JsonNode versionNode = requiredObject(root, "envelopeVersion");
            EventVersion version = EventVersion.of(
                    requiredInt(versionNode, "major"), requiredInt(versionNode, "minor"));
            Class<? extends BusinessEvent> payloadType = registry.find(eventType, version)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unregistered event contract: " + eventType + "@" + version));

            JsonNode metadataNode = requiredObject(root, "metadata");
            EventMetadata metadata = new EventMetadata(
                    EventId.fromString(requiredText(metadataNode, "eventId")),
                    optionalText(metadataNode, "correlationId") == null ? null
                            : CorrelationId.of(optionalText(metadataNode, "correlationId")),
                    optionalText(metadataNode, "causationId") == null ? null
                            : CausationId.of(optionalText(metadataNode, "causationId")),
                    optionalUuid(metadataNode, "tenantId"),
                    optionalUuid(metadataNode, "participantId"),
                    optionalText(metadataNode, "actorId"),
                    optionalText(metadataNode, "occurredAt"),
                    optionalText(metadataNode, "traceId"));

            JsonNode payloadNode = root.get("payload");
            if (payloadNode == null || payloadNode.isNull() || !payloadNode.isObject()) {
                throw new IllegalArgumentException("Event payload must be a JSON object");
            }
            BusinessEvent payload = mapper.treeToValue(payloadNode, payloadType);
            if (!eventType.equals(payload.eventType()) || !version.equals(payload.eventVersion())) {
                throw new IllegalArgumentException("Payload contract identity does not match envelope");
            }
            return new EventEnvelope<>(metadata, version, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed event wire payload", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read event wire payload", e);
        }
    }

    private static void requireObject(JsonNode node, String name) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(name + " must be a JSON object");
        }
    }

    private static JsonNode requiredObject(JsonNode parent, String name) {
        JsonNode node = parent.get(name);
        requireObject(node, name);
        return node;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Required event field is missing: " + field);
        }
        return value;
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Event field must be text: " + field);
        }
        return value.textValue();
    }

    private static int requiredInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToInt()) {
            throw new IllegalArgumentException("Required integer event field is missing: " + field);
        }
        return value.intValue();
    }

    private static UUID optionalUuid(JsonNode node, String field) {
        String value = optionalText(node, field);
        return value == null ? null : UUID.fromString(value);
    }

    private static String string(UUID value) {
        return value == null ? null : value.toString();
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field);
        else node.put(field, value);
    }
}
