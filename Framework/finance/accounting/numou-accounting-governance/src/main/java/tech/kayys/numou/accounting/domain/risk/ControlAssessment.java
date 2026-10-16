package tech.kayys.syirkah.accounting.domain.risk;

import java.time.LocalDate;
import java.util.Objects;

public record ControlAssessment(
        String controlId,
        String riskId,
        String controlName,
        boolean effective,
        LocalDate assessmentDate
) {
    public ControlAssessment {
        Objects.requireNonNull(controlId, "controlId must not be null");
        Objects.requireNonNull(riskId, "riskId must not be null");
        Objects.requireNonNull(controlName, "controlName must not be null");
        Objects.requireNonNull(assessmentDate, "assessmentDate must not be null");
    }
}
