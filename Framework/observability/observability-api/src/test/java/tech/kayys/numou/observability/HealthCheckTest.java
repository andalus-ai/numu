package tech.kayys.syirkah.observability;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Health Checks & Evaluation Tests")
class HealthCheckTest {

    @Test
    @DisplayName("Should aggregate health check results correctly")
    void shouldAggregateHealthCheckResults() {
        HealthCheckResult up1 = HealthCheckResult.up("db", "connected", Duration.ofMillis(10));
        HealthCheckResult up2 = HealthCheckResult.up("outbox", "ready", Duration.ofMillis(5));
        assertEquals(HealthStatus.Status.UP, HealthAggregator.aggregate(List.of(up1, up2)));

        HealthCheckResult degraded = HealthCheckResult.degraded("cache", "slow", Duration.ofMillis(500));
        assertEquals(HealthStatus.Status.DEGRADED, HealthAggregator.aggregate(List.of(up1, degraded)));

        HealthCheckResult down = HealthCheckResult.down("auth", "offline", Duration.ofMillis(20));
        assertEquals(HealthStatus.Status.DOWN, HealthAggregator.aggregate(List.of(up1, degraded, down)));
    }

    @Test
    @DisplayName("Should evaluate registered checks with failure isolation")
    void shouldEvaluateWithFailureIsolation() {
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry();

        HealthCheck goodCheck = new HealthCheck() {
            @Override public String id() { return "good"; }
            @Override public String name() { return "Good Check"; }
            @Override public HealthCheckCategory category() { return HealthCheckCategory.DATABASE; }
            @Override public HealthCheckCriticality criticality() { return HealthCheckCriticality.REQUIRED; }
            @Override public Uni<HealthCheckResult> check() {
                return Uni.createFrom().item(HealthCheckResult.up("good", "OK", Duration.ofMillis(5)));
            }
        };

        HealthCheck failingCheck = new HealthCheck() {
            @Override public String id() { return "failing"; }
            @Override public String name() { return "Failing Check"; }
            @Override public HealthCheckCategory category() { return HealthCheckCategory.EXTERNAL_DEPENDENCY; }
            @Override public HealthCheckCriticality criticality() { return HealthCheckCriticality.OPTIONAL; }
            @Override public Uni<HealthCheckResult> check() {
                return Uni.createFrom().failure(new IllegalStateException("Network unreachable"));
            }
        };

        registry.register(goodCheck);
        registry.register(failingCheck);

        HealthEvaluator evaluator = new DefaultHealthEvaluator(registry);
        HealthReport report = evaluator.evaluate().await().indefinitely();

        assertNotNull(report);
        assertEquals(HealthStatus.Status.DOWN, report.status());
        assertEquals(2, report.checks().size());

        assertTrue(report.checks().stream().anyMatch(c -> c.checkId().equals("good") && c.isUp()));
        assertTrue(report.checks().stream().anyMatch(c -> c.checkId().equals("failing") && c.isDown()));
    }

    @Test
    @DisplayName("Should prevent registry mutation once frozen")
    void shouldPreventMutationOnceFrozen() {
        DefaultHealthCheckRegistry registry = new DefaultHealthCheckRegistry();
        assertFalse(registry.isFrozen());

        registry.freeze();
        assertTrue(registry.isFrozen());

        HealthCheck check = new HealthCheck() {
            @Override public String id() { return "c"; }
            @Override public String name() { return "c"; }
            @Override public HealthCheckCategory category() { return HealthCheckCategory.RUNTIME; }
            @Override public HealthCheckCriticality criticality() { return HealthCheckCriticality.REQUIRED; }
            @Override public Uni<HealthCheckResult> check() { return Uni.createFrom().nullItem(); }
        };

        assertThrows(IllegalStateException.class, () -> registry.register(check));
        assertThrows(IllegalStateException.class, () -> registry.unregister("c"));
    }
}
