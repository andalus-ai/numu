package tech.kayys.numou.foundation.domain.identifier;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Audit record of an allocated business identifier (config03.md §P4-15 #3).
 */
public record IdentifierAllocation(
        BusinessIdentifier identifier,
        IdentifierPolicyId policyId,
        IdentifierScope scope,
        long sequenceNumber,
        Instant allocatedAt
) implements Serializable {

    public IdentifierAllocation {
        Objects.requireNonNull(identifier, "identifier cannot be null");
        Objects.requireNonNull(policyId, "policyId cannot be null");
        Objects.requireNonNull(scope, "scope cannot be null");
        Objects.requireNonNull(allocatedAt, "allocatedAt cannot be null");
        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
    }
}
