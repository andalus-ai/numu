package tech.kayys.numou.automotiveparts.domain;

import java.util.Locale;

/** Vehicle attributes supplied by a fitment search or vehicle-registry adapter. */
public record VehicleSpecification(String market, String make, String model, String generation,
                                  int modelYear, String engineCode, String trim) {
    public VehicleSpecification {
        market = normalize(market);
        make = required(make, "make");
        model = required(model, "model");
        generation = normalize(generation);
        engineCode = normalize(engineCode);
        trim = normalize(trim);
        if (modelYear < 1886 || modelYear > 2200) throw new IllegalArgumentException("modelYear is outside supported bounds");
    }

    private static String required(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) throw new IllegalArgumentException(field + " is required");
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}