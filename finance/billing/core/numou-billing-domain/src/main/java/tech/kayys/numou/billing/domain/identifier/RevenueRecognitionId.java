package tech.kayys.numou.billing.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record RevenueRecognitionId(UUID value) implements DomainId<UUID>, Serializable {

    public RevenueRecognitionId {
        Objects.requireNonNull(value, "RevenueRecognitionId value cannot be null");
    }

    public static RevenueRecognitionId of(UUID value) {
        return new RevenueRecognitionId(value);
    }

    public static RevenueRecognitionId generate() {
        return new RevenueRecognitionId(UUID.randomUUID());
    }

    public static RevenueRecognitionId fromString(String value) {
        return new RevenueRecognitionId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "RevenueRecognitionId{" + value + "}";
    }
}