package tech.kayys.syirkah.accounting.domain.risk;

import java.util.Objects;
import java.util.UUID;

public record RiskIncidentId(String value) {
    public RiskIncidentId {
        Objects.requireNonNull(value, "value must not be null");
        if (value.isBlank()) throw new IllegalArgumentException("RiskIncidentId must not be blank");
    }
    public static RiskIncidentId newId() {
        return new RiskIncidentId(UUID.randomUUID().toString());
    }
    public static RiskIncidentId of(String value) {
        return new RiskIncidentId(value);
    }
}
