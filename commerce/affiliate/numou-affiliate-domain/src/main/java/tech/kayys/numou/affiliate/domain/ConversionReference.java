package tech.kayys.numou.affiliate.domain;
import java.time.Instant;
import java.util.Objects;
/** Stable source reference returned only after the source adapter verifies a qualifying lifecycle state. */
public record ConversionReference(String sourceType, String sourceId, String eventId, String subjectKey, Instant occurredAt) {
    public ConversionReference {
        sourceType = required(sourceType, "sourceType");
        sourceId = required(sourceId, "sourceId");
        eventId = required(eventId, "eventId");
        subjectKey = required(subjectKey, "subjectKey");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }
    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        String v = value.trim();
        if (v.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return v;
    }
}
