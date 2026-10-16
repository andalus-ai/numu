package tech.kayys.numou.asset.domain.warranty;

import java.util.Objects;
import java.util.UUID;

/** Exclusion code with human description (ASSET-23): interpretation is config, not booleans. */
public record WarrantyExclusion(UUID id, String code, String description) {
    public WarrantyExclusion {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(code, "code cannot be null");
        if (code.isBlank()) throw new IllegalArgumentException("code cannot be blank");
    }
}
