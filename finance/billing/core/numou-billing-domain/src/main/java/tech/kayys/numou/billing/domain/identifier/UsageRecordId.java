package tech.kayys.numou.billing.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

public record UsageRecordId(UUID value) implements DomainId<UUID>, Serializable {

    public UsageRecordId {
        Objects.requireNonNull(value, "UsageRecordId value cannot be null");
    }

    public static UsageRecordId of(UUID value) {
        return new UsageRecordId(value);
    }

    public static UsageRecordId generate() {
        return new UsageRecordId(UUID.randomUUID());
    }

    public static UsageRecordId fromString(String value) {
        return new UsageRecordId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "UsageRecordId{" + value + "}";
    }
}