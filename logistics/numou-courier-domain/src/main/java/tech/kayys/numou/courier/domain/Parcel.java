package tech.kayys.numou.courier.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** One independently identified physical piece in a multi-piece shipment. */
public record Parcel(
        ParcelId id, String reference, BigDecimal weightKg,
        BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
        String handlingInstructions) {
    public Parcel {
        Objects.requireNonNull(id, "id must not be null");
        reference = required(reference, "reference");
        weightKg = positive(weightKg, "weightKg");
        lengthCm = positive(lengthCm, "lengthCm");
        widthCm = positive(widthCm, "widthCm");
        heightCm = positive(heightCm, "heightCm");
        handlingInstructions = handlingInstructions == null || handlingInstructions.isBlank()
                ? null : handlingInstructions.trim();
    }
    private static BigDecimal positive(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.signum() <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value.stripTrailingZeros();
    }
    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String result = value.trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return result;
    }
    public BigDecimal volumetricWeightKg(BigDecimal divisor) {
        Objects.requireNonNull(divisor, "divisor must not be null");
        if (divisor.signum() <= 0) throw new IllegalArgumentException("divisor must be positive");
        return lengthCm.multiply(widthCm).multiply(heightCm)
                .divide(divisor, 3, java.math.RoundingMode.HALF_UP);
    }
}