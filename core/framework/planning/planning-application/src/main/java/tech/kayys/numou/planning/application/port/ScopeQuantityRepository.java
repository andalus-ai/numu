package tech.kayys.numou.planning.application.port;

import tech.kayys.numou.planning.domain.QuantityMeasurement;
import tech.kayys.numou.planning.domain.ScopeQuantityDefinition;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-explicit persistence boundary for scope quantities. Implementations must never
 * resolve a definition or measurement outside the supplied tenant scope.
 */
public interface ScopeQuantityRepository {
    /** Creates a new immutable definition revision; returns false if that ID already exists. */
    boolean createDefinition(ScopeQuantityDefinition definition);

    Optional<ScopeQuantityDefinition> findDefinition(UUID tenantId, UUID definitionId);

    List<ScopeQuantityDefinition> findDefinitionsForScope(
            UUID tenantId, String scopeType, String scopeId);

    /** Returns false for identical idempotent replay; conflicting key reuse is rejected. */
    boolean appendMeasurement(QuantityMeasurement measurement);

    List<QuantityMeasurement> findMeasurementHistory(UUID tenantId, UUID definitionId);

    Optional<QuantityMeasurement> findLatestMeasurementAsOf(
            UUID tenantId, UUID definitionId, LocalDate asOfDate);
}
