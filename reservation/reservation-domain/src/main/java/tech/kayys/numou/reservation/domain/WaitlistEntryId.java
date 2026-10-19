package tech.kayys.numou.reservation.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record WaitlistEntryId(UUID value) implements DomainId<UUID> {
    public WaitlistEntryId {
        Objects.requireNonNull(value, "WaitlistEntryId value cannot be null");
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
}
