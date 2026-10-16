package tech.kayys.syirkah.accounting.domain.maintenance;

import java.math.BigDecimal;
import java.util.Objects;

public record WorkOrderPart(
        String partNumber,
        String description,
        int quantity,
        BigDecimal unitCost
) {
    public WorkOrderPart {
        Objects.requireNonNull(partNumber, "partNumber must not be null");
        Objects.requireNonNull(description, "description must not be null");
        unitCost = Objects.requireNonNullElse(unitCost, BigDecimal.ZERO);
    }

    public BigDecimal totalCost() {
        return unitCost.multiply(BigDecimal.valueOf(quantity));
    }
}
