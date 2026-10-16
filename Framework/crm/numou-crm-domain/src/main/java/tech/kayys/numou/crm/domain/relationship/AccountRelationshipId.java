package tech.kayys.numou.crm.domain.relationship;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Identifier for an {@link AccountRelationship}.
 */
public record AccountRelationshipId(UUID value) implements DomainId<UUID>, Serializable {
        public AccountRelationshipId {
        value = Objects.requireNonNull(value, "value cannot be null");
    }

    public static AccountRelationshipId generate() {
        return new AccountRelationshipId(UUID.randomUUID());
    }

    public static AccountRelationshipId of(UUID value) {
        return new AccountRelationshipId(value);
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}