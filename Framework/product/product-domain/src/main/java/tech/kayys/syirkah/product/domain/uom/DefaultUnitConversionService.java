package tech.kayys.syirkah.product.domain.uom;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standard in-memory implementation of {@link UnitConversionService}.
 * Supports direct and reciprocal conversion with configured precision and rounding.
 */
public class DefaultUnitConversionService implements UnitConversionService {

    private final Map<String, UnitConversion> conversions = new ConcurrentHashMap<>();

    public void register(UnitConversion conversion) {
        Objects.requireNonNull(conversion, "conversion cannot be null");
        String key = key(conversion.from(), conversion.to());
        conversions.put(key, conversion);
    }

    @Override
    public Quantity convert(Quantity source, UnitOfMeasure target, RoundingPolicy roundingPolicy) {
        Objects.requireNonNull(source, "source cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(roundingPolicy, "roundingPolicy cannot be null");

        if (source.unit().equals(target)) {
            return source.round(roundingPolicy);
        }

        if (source.unit().category() != target.category()) {
            throw new IllegalArgumentException(
                    "Cannot convert between different categories: "
                            + source.unit().category() + " -> " + target.category()
            );
        }

        // 1. Direct conversion
        String directKey = key(source.unit(), target);
        UnitConversion direct = conversions.get(directKey);
        if (direct != null) {
            BigDecimal converted = direct.convert(source.amount())
                    .setScale(roundingPolicy.scale(), roundingPolicy.mode());
            return new Quantity(converted, target);
        }

        // 2. Reciprocal conversion
        String reverseKey = key(target, source.unit());
        UnitConversion reverse = conversions.get(reverseKey);
        if (reverse != null) {
            BigDecimal reciprocalAmount = source.amount()
                    .divide(reverse.factor(), roundingPolicy.scale(), roundingPolicy.mode());
            return new Quantity(reciprocalAmount, target);
        }

        throw new IllegalArgumentException(
                "No conversion rule found from " + source.unit().code() + " to " + target.code()
        );
    }

    private static String key(UnitOfMeasure from, UnitOfMeasure to) {
        return from.code() + "->" + to.code();
    }
}
