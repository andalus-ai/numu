package tech.kayys.numou.mobility.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.mobility.ride.domain.event.RideCancelled;
import tech.kayys.numou.mobility.ride.domain.event.RideCompleted;
import tech.kayys.numou.mobility.ride.domain.event.RideNoDrivers;
import tech.kayys.numou.reliability.adapter.json.JsonDomainEvent;

import java.util.Map;
import java.util.Optional;

/** Rebuilds the typed ride event a policy needs from the generic wire payload; other event types are acknowledged and ignored. */
public final class MobilityEventDecoder {

    private static final Map<String, Class<? extends DomainEvent>> HANDLED = Map.of(
            "mobility.ride.completed", RideCompleted.class,
            "mobility.ride.cancelled", RideCancelled.class,
            "mobility.ride.no-drivers", RideNoDrivers.class);

    private final ObjectMapper mapper;

    public MobilityEventDecoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<DomainEvent> decode(JsonDomainEvent wire) {
        final var type = HANDLED.get(wire.eventType());
        if (type == null) return Optional.empty();
        final ObjectNode node = wire.data().deepCopy();
        node.put("eventId", wire.eventId().toString());
        node.put("occurredAt", wire.occurredAtIso());
        try {
            return Optional.of(mapper.treeToValue(node, type));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot decode " + wire.eventType(), e); // permanent: retrying cannot fix a malformed payload
        }
    }
}
