package tech.kayys.syirkah.ecosystem.domain.party;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed canonical identifier for a Party (config02.md §P4-11 #7).
 */
public record PartyId(UUID value) implements DomainId<UUID> {

    public PartyId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static PartyId generate() {
        return new PartyId(UUID.randomUUID());
    }

    public static PartyId of(UUID value) {
        return new PartyId(value);
    }

    public static PartyId fromString(String uuid) {
        return new PartyId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
