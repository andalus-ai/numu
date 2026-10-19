package tech.kayys.numou.crm.domain.identifier;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/**
 * Lead identifier.
 */
public record LeadId(UUID value) implements DomainId<UUID>, Serializable {

    public LeadId {
        Objects.requireNonNull(value, "LeadId value cannot be null");
    }

    public static LeadId of(UUID value) {
        return new LeadId(value);
    }

    public static LeadId generate() {
        return new LeadId(UUID.randomUUID());
    }

    public static LeadId fromString(String value) {
        return new LeadId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return "LeadId{" + value + "}";
    }
}