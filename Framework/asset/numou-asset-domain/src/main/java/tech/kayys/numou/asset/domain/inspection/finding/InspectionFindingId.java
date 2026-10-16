package tech.kayys.syirkah.asset.domain.inspection.finding;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.io.Serializable;
import java.util.UUID;

/** Identifier of an {@link InspectionFinding}. */
public record InspectionFindingId(UUID value) implements DomainId<UUID>, Serializable {

    public InspectionFindingId {
        Objects.requireNonNull(value, "InspectionFindingId value cannot be null");
    }

    public static InspectionFindingId of(UUID value) {
        return new InspectionFindingId(value);
    }

    public static InspectionFindingId generate() {
        return new InspectionFindingId(UUID.randomUUID());
    }

    public static InspectionFindingId fromString(String value) {
        return new InspectionFindingId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "";
    }
}
