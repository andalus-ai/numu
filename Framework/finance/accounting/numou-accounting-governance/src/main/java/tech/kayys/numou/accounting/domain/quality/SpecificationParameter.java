package tech.kayys.syirkah.accounting.domain.quality;

import java.util.Objects;

public record SpecificationParameter(
        String name,
        double targetValue,
        double minValue,
        double maxValue,
        String unitOfMeasure,
        boolean isCritical
) {
    public SpecificationParameter {
        Objects.requireNonNull(name, "name must not be null");
        if (minValue > maxValue) {
            throw new IllegalArgumentException("minValue cannot exceed maxValue");
        }
    }

    public boolean isWithinTolerance(double measured) {
        return measured >= minValue && measured <= maxValue;
    }
}
