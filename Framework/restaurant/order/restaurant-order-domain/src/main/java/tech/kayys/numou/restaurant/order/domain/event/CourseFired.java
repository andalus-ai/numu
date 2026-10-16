package tech.kayys.syirkah.restaurant.order.domain.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * "Cook this now." Carries everything the kitchen needs, so the kitchen context never has to call back into
 * orders. Contains no guest personal data.
 */
public record CourseFired(
        UUID eventId,
        Instant occurredAt,
        UUID orderId,
        UUID venueId,
        int course,
        String tableLabel,
        List<Line> lines) implements DomainEvent {

    public record Line(UUID lineId, UUID menuItemId, String name, int quantity, List<String> modifiers, String notes,
                       List<String> allergens, long prepSeconds, UUID stationId) { }

    public static CourseFired at(Instant now, UUID orderId, UUID venueId, int course, String tableLabel, List<Line> lines) {
        return new CourseFired(UUID.randomUUID(), now, orderId, venueId, course, tableLabel, lines);
    }

    @Override
    public String eventType() {
        return "restaurant.order.course-fired";
    }
}
