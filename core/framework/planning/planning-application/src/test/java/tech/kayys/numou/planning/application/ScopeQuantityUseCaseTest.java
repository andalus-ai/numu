package tech.kayys.numou.planning.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.application.port.ScopeQuantityRepository;
import tech.kayys.numou.planning.domain.QuantityMeasurement;
import tech.kayys.numou.planning.domain.ScopeQuantityDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ScopeQuantityUseCaseTest {
    @Test
    void createsSequentialRevisionsWithinTenantScope() {
        var repository = new FakeRepository();
        var create = new CreateScopeQuantityDefinition(repository);
        UUID tenant = UUID.randomUUID();
        var first = definition(tenant, UUID.randomUUID(), "10", 1);
        assertEquals(first, create.execute(first));
        var second = definition(tenant, UUID.randomUUID(), "12", 2);
        assertEquals(second, create.execute(second));
        assertThrows(IllegalArgumentException.class,
                () -> create.execute(definition(tenant, UUID.randomUUID(), "13", 4)));
    }

    @Test
    void measurementCannotReferenceDefinitionOutsideItsTenant() {
        var repository = new FakeRepository();
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        var definition = definition(tenantA, UUID.randomUUID(), "10", 1);
        repository.createDefinition(definition);
        var measurement = new QuantityMeasurement(UUID.randomUUID(), tenantB, definition.id(),
                LocalDate.of(2026, 10, 20), new BigDecimal("3"),
                java.time.Instant.parse("2026-10-20T10:00:00Z"), "planner", "key-1", null);
        assertThrows(IllegalArgumentException.class,
                () -> new RecordQuantityMeasurement(repository).execute(measurement));
    }

    private ScopeQuantityDefinition definition(UUID tenant, UUID id, String amount, int revision) {
        return new ScopeQuantityDefinition(id, tenant, "WBS_NODE", "node-1",
                "DELIVERABLE", "Delivery units", "unit", new BigDecimal(amount), revision);
    }

    private static final class FakeRepository implements ScopeQuantityRepository {
        private final Map<UUID, ScopeQuantityDefinition> definitions = new HashMap<>();
        private final List<QuantityMeasurement> measurements = new ArrayList<>();

        @Override public boolean createDefinition(ScopeQuantityDefinition d) {
            if (definitions.containsKey(d.id())) return false;
            definitions.put(d.id(), d);
            return true;
        }

        @Override public Optional<ScopeQuantityDefinition> findDefinition(UUID tenant, UUID id) {
            var d = definitions.get(id);
            return d != null && d.tenantId().equals(tenant) ? Optional.of(d) : Optional.empty();
        }

        @Override public List<ScopeQuantityDefinition> findDefinitionsForScope(UUID tenant, String type, String id) {
            return definitions.values().stream()
                    .filter(d -> d.tenantId().equals(tenant) && d.scopeType().equals(type) && d.scopeId().equals(id))
                    .sorted(Comparator.comparing(ScopeQuantityDefinition::code)
                            .thenComparingInt(ScopeQuantityDefinition::revision)).toList();
        }

        @Override public boolean appendMeasurement(QuantityMeasurement m) {
            if (measurements.stream().anyMatch(existing -> existing.tenantId().equals(m.tenantId())
                    && existing.idempotencyKey().equals(m.idempotencyKey()))) return false;
            measurements.add(m);
            return true;
        }

        @Override public List<QuantityMeasurement> findMeasurementHistory(UUID tenant, UUID definitionId) {
            return measurements.stream().filter(m -> m.tenantId().equals(tenant)
                    && m.definitionId().equals(definitionId)).toList();
        }

        @Override public Optional<QuantityMeasurement> findLatestMeasurementAsOf(
                UUID tenant, UUID definitionId, LocalDate asOf) {
            return findMeasurementHistory(tenant, definitionId).stream()
                    .filter(m -> !m.asOfDate().isAfter(asOf))
                    .max(Comparator.comparing(QuantityMeasurement::asOfDate)
                            .thenComparing(QuantityMeasurement::recordedAt));
        }
    }
}
