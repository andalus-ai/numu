package tech.kayys.numou.workforce.domain.employment;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

/**
 * Strongly typed identifier for an Employment relationship.
 */
public record EmploymentId(UUID value) implements DomainId<UUID> {

    public EmploymentId {
        Objects.requireNonNull(value, "Employment ID must not be null");
    }

    public static EmploymentId generate() {
        return new EmploymentId(UUID.randomUUID());
    }

    public static EmploymentId of(UUID value) {
        return new EmploymentId(value);
    }
}
