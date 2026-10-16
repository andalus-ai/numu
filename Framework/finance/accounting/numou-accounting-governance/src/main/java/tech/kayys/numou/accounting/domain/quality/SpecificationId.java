package tech.kayys.syirkah.accounting.domain.quality;

import java.util.Objects;
import java.util.UUID;

public record SpecificationId(String value) {
    public SpecificationId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("SpecificationId must not be blank");
    }
    public static SpecificationId newId() {
        return new SpecificationId(UUID.randomUUID().toString());
    }
    public static SpecificationId of(String value) {
        return new SpecificationId(value);
    }
}
