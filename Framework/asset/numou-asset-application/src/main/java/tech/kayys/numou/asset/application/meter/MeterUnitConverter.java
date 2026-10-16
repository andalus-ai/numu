package tech.kayys.numou.asset.application.meter;

import tech.kayys.numou.asset.domain.meter.MeterUnit;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Application-side unit normalisation (ASSET-21 §21.15).
 *
 * <p>The domain only accepts the meter's canonical unit; this converter maps
 * compatible input units to canonical values before persistence. Minimum
 * supported conversion is MILE → KM (1 mi = 1.609344 km).</p>
 */
public final class MeterUnitConverter {

    private static final BigDecimal MILE_TO_KM = new BigDecimal("1.609344");

    private MeterUnitConverter() {
    }

    public static BigDecimal toCanonical(BigDecimal value, MeterUnit input, MeterUnit canonical) {
        if (input == canonical) {
            return value;
        }
        if (input == MeterUnit.MILE && canonical == MeterUnit.KM) {
            return value.multiply(MILE_TO_KM, MathContext.DECIMAL128);
        }
        if (input == MeterUnit.KM && canonical == MeterUnit.MILE) {
            return value.divide(MILE_TO_KM, MathContext.DECIMAL128);
        }
        throw new IllegalArgumentException(
                "Cannot convert reading unit " + input + " to meter canonical unit " + canonical);
    }
}
