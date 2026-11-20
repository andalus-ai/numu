package tech.kayys.numou.plugin.application.runtime;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.observability.HealthCheck;
import tech.kayys.numou.observability.HealthCheckCategory;
import tech.kayys.numou.observability.HealthCheckCriticality;
import tech.kayys.numou.observability.HealthCheckResult;
import tech.kayys.numou.observability.HealthStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Adapts one application runtime session to Numou's shared health-check registry contract.
 * The check reports aggregate readiness only; provider identifiers and exception details remain
 * available through the protected in-process ApplicationRuntimeHealth snapshot.
 */
public final class ApplicationRuntimeHealthCheck implements HealthCheck {

    private static final String CHECK_ID_PREFIX = "application-runtime:";

    private final ApplicationRuntimeSession session;
    private final String checkId;

    public ApplicationRuntimeHealthCheck(ApplicationRuntimeSession session) {
        this.session = Objects.requireNonNull(session, "Application runtime session is required");
        this.checkId = CHECK_ID_PREFIX + session.applicationId();
    }

    @Override public String id() { return checkId; }

    @Override public String name() { return "Application runtime readiness"; }

    @Override public HealthCheckCategory category() { return HealthCheckCategory.RUNTIME; }

    @Override public HealthCheckCriticality criticality() { return HealthCheckCriticality.REQUIRED; }

    @Override
    public Uni<HealthCheckResult> check() {
        return Uni.createFrom().item(() -> {
            long startedAt = System.nanoTime();
            ApplicationRuntimeHealth snapshot = session.health();
            long readyProviders = snapshot.providers().stream()
                    .filter(ApplicationRuntimeProviderHealth::ready).count();
            String message = snapshot.ready()
                    ? "ready: " + readyProviders + "/" + snapshot.providers().size() + " providers"
                    : "not ready: " + readyProviders + "/" + snapshot.providers().size()
                            + " providers" + (snapshot.stopped() ? "; stopped" : "");
            return new HealthCheckResult(checkId,
                    snapshot.ready() ? HealthStatus.Status.UP : HealthStatus.Status.DOWN,
                    message, Instant.now(), Duration.ofNanos(System.nanoTime() - startedAt));
        });
    }
}
