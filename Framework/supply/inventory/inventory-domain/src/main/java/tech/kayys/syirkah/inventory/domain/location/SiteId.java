package tech.kayys.syirkah.inventory.domain.location;

import java.util.Objects;
import java.util.UUID;

/**
 * Identity of an operational site.
 */
public record SiteId(String value) {
    public SiteId {
        Objects.requireNonNull(value, "value cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("SiteId cannot be blank");
        }
    }

    public static SiteId generate() {
        return new SiteId(UUID.randomUUID().toString());
    }

    public static SiteId of(String value) {
        return new SiteId(value);
    }
}
