package tech.kayys.syirkah.accounting.domain.audit;

import java.util.Objects;
import java.util.UUID;

public record FollowUpId(String value) {
    public FollowUpId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("FollowUpId must not be blank");
    }
    public static FollowUpId newId() {
        return new FollowUpId(UUID.randomUUID().toString());
    }
    public static FollowUpId of(String value) {
        return new FollowUpId(value);
    }
}
