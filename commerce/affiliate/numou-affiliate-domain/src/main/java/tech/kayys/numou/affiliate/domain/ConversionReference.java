package tech.kayys.numou.affiliate.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Stable reference to a conversion whose qualifying state has already been verified by the source adapter.
 * Source type and IDs are correlation data, not proof of payment or authorization.
 */
public record ConversionReference(
        String sourceType,
        String sourceId,
        String eventId,
        String subjectKey,
        Instant occurredAt) {

    public ConversionReference {
        sourceType = required(sourceType, "sourceType");
        sourceId = required(sourceId, "sourceId");
        eventId = required(eventId, "eventId");
        subjectKey = required(subjectKey, "subjectKey");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String normalized = value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return normalized;
    }
}
