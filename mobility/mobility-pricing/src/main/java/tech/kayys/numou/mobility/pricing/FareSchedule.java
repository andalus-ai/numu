package tech.kayys.numou.mobility.pricing;

import tech.kayys.numou.mobility.kernel.domain.VehicleType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * City tariff. Rates are configuration (they differ by city and change often); the defaults below are illustrative
 * Jakarta-style numbers, not a published tariff.
 *
 * @param roundingUnit fares are rounded <em>up</em> to a multiple of this (IDR 500: nobody has coins for Rp 28,350)
 */
public record FareSchedule(String currency, int fractionDigits, BigDecimal roundingUnit, Map<VehicleType, Rate> rates) {

    public FareSchedule {
        Objects.requireNonNull(currency, "currency cannot be null");
        Objects.requireNonNull(roundingUnit, "roundingUnit cannot be null");
        if (roundingUnit.signum() <= 0) {
            throw new IllegalArgumentException("roundingUnit must be positive");
        }
        rates = Map.copyOf(new EnumMap<>(rates));
    }

    public Rate rate(VehicleType v) {
        final var r = rates.get(v);
        if (r == null) {
            throw new IllegalArgumentException("No tariff for " + v);
        }
        return r;
    }

    BigDecimal roundUp(BigDecimal amount) {
        return amount.divide(roundingUnit, 0, RoundingMode.CEILING).multiply(roundingUnit).setScale(fractionDigits, RoundingMode.UNNECESSARY);
    }

    BigDecimal scale(BigDecimal amount) {
        return amount.setScale(fractionDigits, RoundingMode.HALF_UP);
    }

    public static FareSchedule indonesiaDefaults() {
        final var m = new EnumMap<VehicleType, Rate>(VehicleType.class);
        m.put(VehicleType.BIKE, Rate.of(2_000, 2_300, 150, 8_000, 1_000, 5_000, 8_000));
        m.put(VehicleType.CAR, Rate.of(5_000, 3_500, 400, 14_000, 1_000, 8_000, 12_000));
        m.put(VehicleType.CAR_XL, Rate.of(7_000, 4_500, 500, 20_000, 1_500, 10_000, 15_000));
        return new FareSchedule("IDR", 0, BigDecimal.valueOf(500), m);
    }
}
