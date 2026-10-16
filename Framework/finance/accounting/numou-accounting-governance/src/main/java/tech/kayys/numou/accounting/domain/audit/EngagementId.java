package tech.kayys.syirkah.accounting.domain.audit;

import java.util.Objects;
import java.util.UUID;

public record EngagementId(String value) {
    public EngagementId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("EngagementId must not be blank");
    }
    public static EngagementId newId() {
        return new EngagementId(UUID.randomUUID().toString());
    }
    public static EngagementId of(String value) {
        return new EngagementId(value);
    }
}
