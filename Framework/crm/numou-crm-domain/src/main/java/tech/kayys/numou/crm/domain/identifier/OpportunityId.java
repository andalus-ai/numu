package tech.kayys.numou.crm.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Opportunity identifier.
 */
public record OpportunityId(UUID value) implements DomainId<UUID>, Serializable {

    public OpportunityId {
        Objects.requireNonNull(value, "OpportunityId value cannot be null");
    }

    public static OpportunityId of(UUID value) {
        return new OpportunityId(value);
    }

    public static OpportunityId generate() {
        return new OpportunityId(UUID.randomUUID());
    }

    public static OpportunityId fromString(String value) {
        return new OpportunityId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "OpportunityId{" + value + "}";
    }
}