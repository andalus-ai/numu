package tech.kayys.numou.automotiveparts.domain;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

/**
 * A fitment claim scoped to a tenant and market.
 * Null optional constraints mean "not constrained by this record", not proof of universal compatibility.
 */
public record VehicleFitment(String tenantId, String market, String make, String model, String generation,
                             int fromModelYear, int throughModelYear, String engineCode, String trim,
                             String evidenceSource, String evidenceReference,
                             LocalDate effectiveFrom, LocalDate effectiveThrough) {
    public VehicleFitment {
        tenantId = requiredTenantId(tenantId);
        market = normalize(market);
        make = required(make, "make");
        model = required(model, "model");
        generation = normalize(generation);
        engineCode = normalize(engineCode);
        trim = normalize(trim);
        evidenceSource = required(evidenceSource, "evidenceSource");
        evidenceReference = normalize(evidenceReference);
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        if (fromModelYear < 1886 || throughModelYear > 2200 || fromModelYear > throughModelYear) {
            throw new IllegalArgumentException("Invalid model year range");
        }
        if (effectiveThrough != null && effectiveThrough.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveThrough cannot precede effectiveFrom");
        }
    }

    public boolean matches(VehicleSpecification vehicle, LocalDate asOf) {
        Objects.requireNonNull(vehicle, "vehicle is required");
        Objects.requireNonNull(asOf, "asOf is required");
        if (vehicle.modelYear() < fromModelYear || vehicle.modelYear() > throughModelYear) return false;
        if (!make.equals(vehicle.make()) || !model.equals(vehicle.model())) return false;
        if (market != null && !market.equals(vehicle.market())) return false;
        if (generation != null && !generation.equals(vehicle.generation())) return false;
        if (engineCode != null && !engineCode.equals(vehicle.engineCode())) return false;
        if (trim != null && !trim.equals(vehicle.trim())) return false;
        if (asOf.isBefore(effectiveFrom)) return false;
        return effectiveThrough == null || !asOf.isAfter(effectiveThrough);
    }

    private static String requiredTenantId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        // Tenant identifiers are opaque security-boundary keys, not market/model labels.
        // Preserve case; uppercasing here makes them disagree with the other tenant-scoped records.
        return value.trim();
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