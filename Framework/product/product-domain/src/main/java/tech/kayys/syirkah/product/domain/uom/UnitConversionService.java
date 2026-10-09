package tech.kayys.syirkah.product.domain.uom;

/**
 * Service port for converting quantities between units of measure.
 */
public interface UnitConversionService {

    /**
     * Converts a quantity to the target unit of measure using the specified rounding policy.
     *
     * @param source source quantity
     * @param target target unit of measure
     * @param roundingPolicy rounding policy to apply to the converted amount
     * @return converted quantity
     * @throws IllegalArgumentException if units are dimensionally incompatible or conversion is not found
     */
    Quantity convert(Quantity source, UnitOfMeasure target, RoundingPolicy roundingPolicy);
}
