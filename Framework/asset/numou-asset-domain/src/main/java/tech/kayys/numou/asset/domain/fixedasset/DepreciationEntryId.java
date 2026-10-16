package tech.kayys.syirkah.asset.domain.fixedasset;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of a depreciation run entry.
 */
public record DepreciationEntryId(String value) {
    public DepreciationEntryId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("DepreciationEntryId must not be blank");
    }

    public static DepreciationEntryId generate() {
        return new DepreciationEntryId(UUID.randomUUID().toString());
    }
}
