package tech.kayys.syirkah.construction.domain.profile;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record ConstructionProjectProfileId(UUID value) implements DomainId<UUID> {
    public ConstructionProjectProfileId {
        Objects.requireNonNull(value, "Construction project profile id cannot be null");
    }

    public static ConstructionProjectProfileId generate() {
        return new ConstructionProjectProfileId(UUID.randomUUID());
    }

    public static ConstructionProjectProfileId of(UUID value) {
        return new ConstructionProjectProfileId(value);
    }
}
