package tech.kayys.numou.foundation.domain.referencedata;

import java.io.Serializable;
import java.util.Objects;

/**
 * Definition and governance rules for a reference data code set (config03.md §P4-16 #4).
 */
public record ReferenceSetDefinition(
        ReferenceSetId id,
        String setKey,
        String displayName,
        GovernanceMode governanceMode,
        String owner,
        long currentVersion
) implements Serializable {

    public ReferenceSetDefinition {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(setKey, "setKey cannot be null");
        Objects.requireNonNull(displayName, "displayName cannot be null");
        Objects.requireNonNull(governanceMode, "governanceMode cannot be null");
        Objects.requireNonNull(owner, "owner cannot be null");
    }
}
