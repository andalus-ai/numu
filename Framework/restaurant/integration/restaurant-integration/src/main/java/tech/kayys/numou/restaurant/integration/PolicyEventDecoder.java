package tech.kayys.numou.restaurant.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.reliability.adapter.json.JsonDomainEvent;
import tech.kayys.numou.restaurant.kitchen.domain.event.TicketItemReady;
import tech.kayys.numou.restaurant.order.domain.event.CourseFired;
import tech.kayys.numou.restaurant.order.domain.event.OrderLineVoided;
import tech.kayys.numou.restaurant.venue.domain.event.TableCleaned;

import java.util.Map;
import java.util.Optional;

/**
 * Rebuilds the typed domain event a policy needs from the generic wire payload. Only event types that have a policy are
 * decoded; everything else is acknowledged and ignored. The envelope carries {@code eventId}/{@code occurredAt}
 * separately, so they are put back into the data before binding.
 */
public final class PolicyEventDecoder {

    private static final Map<String, Class<? extends DomainEvent>> HANDLED = Map.of(
            "restaurant.order.course-fired", CourseFired.class,
            "restaurant.kitchen.item.ready", TicketItemReady.class,
            "restaurant.order.line-voided", OrderLineVoided.class,
            "restaurant.table.cleaned", TableCleaned.class);

    private final ObjectMapper mapper;

    public PolicyEventDecoder(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** @return empty when no policy listens to this event type */
    public Optional<DomainEvent> decode(JsonDomainEvent wire) {
        final var type = HANDLED.get(wire.eventType());
        if (type == null) {
            return Optional.empty();
        }
        final ObjectNode node = wire.data().deepCopy();
        node.put("eventId", wire.eventId().toString());
        node.put("occurredAt", wire.occurredAtIso());
        try {
            return Optional.of(mapper.treeToValue(node, type));
        } catch (JsonProcessingException e) {
            // IllegalArgumentException => classified permanent by consumers: a malformed payload will never decode on retry
            throw new IllegalArgumentException("Cannot decode " + wire.eventType(), e);
        }
    }
}
