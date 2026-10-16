package tech.kayys.syirkah.construction.domain.site;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ConstructionSiteId(UUID value) implements DomainId<UUID> {
    public ConstructionSiteId {
        Objects.requireNonNull(value, "Construction site id cannot be null");
    }

    public static ConstructionSiteId generate() {
        return new ConstructionSiteId(UUID.randomUUID());
    }

    public static ConstructionSiteId of(UUID value) {
        return new ConstructionSiteId(value);
    }
}
