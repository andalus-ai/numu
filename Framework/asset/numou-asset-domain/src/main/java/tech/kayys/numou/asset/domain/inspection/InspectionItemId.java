package tech.kayys.syirkah.asset.domain.inspection;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifier of an {@link InspectionItem}. */
public record InspectionItemId(UUID value) implements DomainId<UUID>, Serializable {

    public InspectionItemId {
        Objects.requireNonNull(value, "InspectionItemId value cannot be null");
    }

    public static InspectionItemId of(UUID value) {
        return new InspectionItemId(value);
    }

    public static InspectionItemId generate() {
        return new InspectionItemId(UUID.randomUUID());
    }

    public static InspectionItemId fromString(String value) {
        return new InspectionItemId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
