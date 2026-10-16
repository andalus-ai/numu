package tech.kayys.syirkah.observability;

import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Standard implementation of HealthEvaluator executing registered checks concurrently with timeout and failure isolation.
 */
public final class DefaultHealthEvaluator implements HealthEvaluator {

    private final HealthCheckRegistry registry;
    private final Duration defaultTimeout;

    public DefaultHealthEvaluator(HealthCheckRegistry registry) {
        this(registry, Duration.ofSeconds(5));
    }

    public DefaultHealthEvaluator(HealthCheckRegistry registry, Duration defaultTimeout) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.defaultTimeout = defaultTimeout != null ? defaultTimeout : Duration.ofSeconds(5);
    }

    @Override
    public Uni<HealthReport> evaluate() {
        List<HealthCheck> checks = registry.checks();
        Instant start = Instant.now();

        if (checks.isEmpty()) {
            return Uni.createFrom().item(new HealthReport(
                    HealthStatus.Status.UP,
                    List.of(),
                    start,
                    Duration.ZERO
            ));
        }

        List<Uni<HealthCheckResult>> checkUnis = checks.stream()
                .map(this::evaluateSafely)
                .toList();

        return Uni.join().all(checkUnis).andCollectFailures()
                .map(results -> {
                    Duration totalDuration = Duration.between(start, Instant.now());
                    HealthStatus.Status aggregated = HealthAggregator.aggregate(results);
                    return new HealthReport(aggregated, results, start, totalDuration);
                });
    }

    private Uni<HealthCheckResult> evaluateSafely(HealthCheck check) {
        Instant checkStart = Instant.now();
        return check.check()
                .ifNoItem().after(defaultTimeout).failWith(
                        new java.util.concurrent.TimeoutException("Health check timed out: " + check.id())
                )
                .onFailure().recoverWithItem(err -> {
                    Duration elapsed = Duration.between(checkStart, Instant.now());
                    String msg = err.getMessage() != null ? err.getMessage() : err.getClass().getSimpleName();
                    return HealthCheckResult.down(check.id(), "Failure: " + msg, elapsed);
                });
    }
}
