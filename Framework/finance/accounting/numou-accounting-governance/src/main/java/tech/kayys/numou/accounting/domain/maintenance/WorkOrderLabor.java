package tech.kayys.syirkah.accounting.domain.maintenance;

import java.math.BigDecimal;
import java.util.Objects;

public record WorkOrderLabor(
        String technician,
        double hoursSpent,
        BigDecimal hourlyRate
) {
    public WorkOrderLabor {
        Objects.requireNonNull(technician, "technician must not be null");
        hourlyRate = Objects.requireNonNullElse(hourlyRate, BigDecimal.ZERO);
    }

    public BigDecimal totalLaborCost() {
        return hourlyRate.multiply(BigDecimal.valueOf(hoursSpent));
    }
}
