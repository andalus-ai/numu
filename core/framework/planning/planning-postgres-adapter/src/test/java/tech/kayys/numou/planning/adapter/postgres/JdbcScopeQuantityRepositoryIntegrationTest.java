package tech.kayys.numou.planning.adapter.postgres;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.domain.QuantityMeasurement;
import tech.kayys.numou.planning.domain.ScopeQuantityDefinition;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class JdbcScopeQuantityRepositoryIntegrationTest {
    @Inject JdbcScopeQuantityRepository repository;

    @Test
    void persistsTenantScopedDefinitionsAndAppendOnlyDatedMeasurements() {
        UUID tenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        var definition = new ScopeQuantityDefinition(definitionId, tenant, "WBS_NODE", "wbs-7",
                "DELIVERABLE", "Completed delivery units", "unit", new BigDecimal("20"), 1);

        assertTrue(repository.createDefinition(definition));
        assertFalse(repository.createDefinition(new ScopeQuantityDefinition(definitionId, tenant, "WBS_NODE",
                "wbs-7", "DELIVERABLE", "Completed delivery units", "UNIT", new BigDecimal("20.000000"), 1)));
        assertTrue(repository.findDefinition(tenant, definitionId).isPresent());
        assertTrue(repository.findDefinition(otherTenant, definitionId).isEmpty());
        assertEquals(1, repository.findDefinitionsForScope(tenant, "WBS_NODE", "wbs-7").size());
        assertTrue(repository.findDefinitionsForScope(otherTenant, "WBS_NODE", "wbs-7").isEmpty());

        var measurement = measurement(tenant, definitionId, "qty-report-1", "4.25");
        assertTrue(repository.appendMeasurement(measurement));
        assertFalse(repository.appendMeasurement(measurement(tenant, definitionId, "qty-report-1", "4.250")));
        assertEquals(1, repository.findMeasurementHistory(tenant, definitionId).size());
        assertEquals(new BigDecimal("4.250000"), repository.findLatestMeasurementAsOf(
                tenant, definitionId, LocalDate.of(2026, 10, 20)).orElseThrow().actualQuantity());
        assertTrue(repository.findMeasurementHistory(otherTenant, definitionId).isEmpty());
        assertTrue(repository.findLatestMeasurementAsOf(
                tenant, definitionId, LocalDate.of(2026, 10, 19)).isEmpty());

        assertThrows(IllegalArgumentException.class,
                () -> repository.appendMeasurement(measurement(tenant, definitionId, "qty-report-1", "5")));
        assertThrows(RuntimeException.class, () -> repository.appendMeasurement(
                measurement(otherTenant, definitionId, "cross-tenant", "2")));
    }

    private QuantityMeasurement measurement(UUID tenant, UUID definition, String key, String amount) {
        return new QuantityMeasurement(UUID.randomUUID(), tenant, definition, LocalDate.of(2026, 10, 20),
                new BigDecimal(amount), Instant.parse("2026-10-20T10:00:00Z"),
                "planner", key, "source-report");
    }
}
