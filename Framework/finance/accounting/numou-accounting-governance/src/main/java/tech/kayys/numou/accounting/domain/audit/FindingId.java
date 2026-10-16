package tech.kayys.syirkah.accounting.domain.audit;

import java.util.Objects;
import java.util.UUID;

public record FindingId(String value) {
    public FindingId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FindingId must not be blank");
    }
    public static FindingId newId() {
        return new FindingId(UUID.randomUUID().toString());
    }
    public static FindingId of(String value) {
        return new FindingId(value);
    }
}
