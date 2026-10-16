package tech.kayys.syirkah.project.domain.risk;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record RiskTreatmentActionId(UUID value)
        implements DomainId<UUID> {

    public RiskTreatmentActionId {
        Objects.requireNonNull(value, "Risk treatment action id cannot be null");
    }

    public static RiskTreatmentActionId generate() {
        return new RiskTreatmentActionId(UUID.randomUUID());
    }

    public static RiskTreatmentActionId of(UUID value) {
        return new RiskTreatmentActionId(value);
    }
}
