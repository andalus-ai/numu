package tech.kayys.numou.foundation.domain.identifier;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Governed numbering policy for an enterprise identifier namespace (config03.md §P4-15 #4).
 */
public record IdentifierPolicy(
        IdentifierPolicyId id,
        String namespace,
        IdentifierFormat format,
        IdentifierPolicyStatus status,
        boolean allowGaps,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) implements Serializable {

    public IdentifierPolicy {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(namespace, "namespace cannot be null");
        Objects.requireNonNull(format, "format cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo cannot precede effectiveFrom");
        }
    }

    public boolean isEffective(LocalDate date) {
        if (status != IdentifierPolicyStatus.ACTIVE) {
            return false;
        }
        if (date == null) {
            return true;
        }
        if (effectiveFrom != null && date.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !date.isAfter(effectiveTo);
    }
}
