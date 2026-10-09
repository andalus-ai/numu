package tech.kayys.syirkah.restaurant.kernel.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record WaitlistEntryId(UUID value) implements DomainId<UUID>, Serializable {

    public WaitlistEntryId {
        Objects.requireNonNull(value, "WaitlistEntryId value must not be null");
    }

    public static WaitlistEntryId of(UUID value) {
        return new WaitlistEntryId(value);
    }

    public static WaitlistEntryId of(String value) {
        return new WaitlistEntryId(UUID.fromString(value));
    }

    public static WaitlistEntryId generate() {
        return new WaitlistEntryId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
