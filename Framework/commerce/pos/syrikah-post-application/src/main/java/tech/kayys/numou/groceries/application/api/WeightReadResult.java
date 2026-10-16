package tech.kayys.syirkah.groceries.application.api;

import java.math.BigDecimal;

public record WeightReadResult(
        String scaleId,
        BigDecimal weightValue,
        String weightUnit,
        BigDecimal tareWeight,
        BigDecimal netWeight,
        boolean isValid,
        String errorMessage
) {}
