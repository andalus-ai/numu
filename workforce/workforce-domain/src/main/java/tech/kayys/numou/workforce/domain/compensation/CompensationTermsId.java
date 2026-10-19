package tech.kayys.numou.workforce.domain.compensation;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record CompensationTermsId(UUID value) implements DomainId<UUID> {
    public CompensationTermsId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static CompensationTermsId of(UUID value) {
        return new CompensationTermsId(value);
    }

    public static CompensationTermsId of(String value) {
        return new CompensationTermsId(UUID.fromString(value));
    }

    public static CompensationTermsId generate() {
        return new CompensationTermsId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
