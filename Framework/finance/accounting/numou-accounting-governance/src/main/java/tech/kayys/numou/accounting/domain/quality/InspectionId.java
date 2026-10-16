package tech.kayys.syirkah.accounting.domain.quality;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record InspectionId(String value) {
    public InspectionId {
        Objects.requireNonNull(value, "InspectionId must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("InspectionId cannot be blank");
    }
    public static InspectionId of(String val) { return new InspectionId(val); }
    public static InspectionId generate() { return new InspectionId(UUID.randomUUID().toString()); }
}
