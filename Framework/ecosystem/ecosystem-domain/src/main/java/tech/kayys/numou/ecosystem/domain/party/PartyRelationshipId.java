package tech.kayys.syirkah.ecosystem.domain.party;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifier for a directed relationship between two parties (config02.md §P4-11 #16).
 */
public record PartyRelationshipId(UUID value) implements DomainId<UUID> {

    public PartyRelationshipId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static PartyRelationshipId generate() {
        return new PartyRelationshipId(UUID.randomUUID());
    }

    public static PartyRelationshipId of(UUID value) {
        return new PartyRelationshipId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
