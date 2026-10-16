package tech.kayys.syirkah.construction.domain.procurement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public record RequirementItem(
        UUID boqItemId,
        String materialCode,
        String description,
        BigDecimal quantity,
        String unit,
        LocalDate requiredDate
) {
    public RequirementItem {
        Objects.requireNonNull(materialCode, "Material code cannot be blank");
        Objects.requireNonNull(quantity, "Quantity cannot be null");
        Objects.requireNonNull(unit, "Unit cannot be null");
        Objects.requireNonNull(requiredDate, "Required date cannot be null");
    }
}
