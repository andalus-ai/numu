package tech.kayys.numou.foundation.domain.identifier;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identity for an enterprise identifier numbering policy (config03.md §P4-15 #3).
 */
public record IdentifierPolicyId(UUID value) implements DomainId<UUID> {

    public IdentifierPolicyId {
        Objects.requireNonNull(value, "value cannot be null");
    }

    public static IdentifierPolicyId generate() {
        return new IdentifierPolicyId(UUID.randomUUID());
    }

    public static IdentifierPolicyId of(UUID value) {
        return new IdentifierPolicyId(value);
    }

    public static IdentifierPolicyId fromString(String uuid) {
        return new IdentifierPolicyId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
