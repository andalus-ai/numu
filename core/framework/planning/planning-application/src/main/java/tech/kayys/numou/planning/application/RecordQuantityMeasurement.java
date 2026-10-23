package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.ScopeQuantityRepository;
import tech.kayys.numou.planning.domain.QuantityMeasurement;

import java.util.Objects;

/**
 * Records dated quantity evidence only when the exact definition belongs to the supplied
 * tenant. Authentication and permission checks must be performed by the invoking boundary.
 */
public final class RecordQuantityMeasurement {
    private final ScopeQuantityRepository repository;

    public RecordQuantityMeasurement(ScopeQuantityRepository repository) {
        this.repository = Objects.requireNonNull(repository, "Scope quantity repository cannot be null");
    }

    /** Returns true when inserted, false for an identical idempotent replay. */
    public boolean execute(QuantityMeasurement measurement) {
        Objects.requireNonNull(measurement, "Measurement cannot be null");
        if (repository.findDefinition(measurement.tenantId(), measurement.definitionId()).isEmpty()) {
            throw new IllegalArgumentException("Quantity definition does not exist in the supplied tenant");
        }
        return repository.appendMeasurement(measurement);
    }
}
