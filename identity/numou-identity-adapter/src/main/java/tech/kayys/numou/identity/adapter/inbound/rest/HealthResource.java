package tech.kayys.numou.identity.adapter.inbound.rest;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.observability.HealthAggregator;
import tech.kayys.numou.observability.HealthCheck;
import tech.kayys.numou.observability.HealthCheckCategory;
import tech.kayys.numou.observability.HealthCheckResult;
import tech.kayys.numou.observability.HealthCheckRegistry;
import tech.kayys.numou.observability.HealthStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kubernetes-style health endpoints backed by the framework {@link HealthCheckRegistry}:
 *
 * <ul>
 *   <li>{@code GET /q/health/live} - liveness: process-level checks only, never dependencies;
 *       a failing database must not restart the pod.</li>
 *   <li>{@code GET /q/health/ready} - readiness: every registered check (database, kafka, ...);
 *       a DOWN result returns HTTP 503 so the instance is taken out of the load balancer.</li>
 * </ul>
 */
@Path("/q/health")
@Tag(name = "Health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    private final HealthCheckRegistry registry;

    @Inject
    public HealthResource(HealthCheckRegistry registry) {
        this.registry = registry;
    }

    @GET
    @Path("/live")
    @Operation(summary = "Liveness probe (process-level checks only)")
    public Uni<Response> liveness() {
        return report(check -> check.category() == HealthCheckCategory.RUNTIME);
    }

    @GET
    @Path("/ready")
    @Operation(summary = "Readiness probe (all dependency checks)")
    public Uni<Response> readiness() {
        return report(check -> true);
    }

    @GET
    @Operation(summary = "Aggregated health report (same as readiness)")
    public Uni<Response> health() {
        return readiness();
    }

    private Uni<Response> report(java.util.function.Predicate<HealthCheck> filter) {
        Instant startedAt = Instant.now();
        List<HealthCheck> checks = registry.checks().stream().filter(filter).toList();
        if (checks.isEmpty()) {
            // e.g. liveness with no RUNTIME checks registered: an empty report is UP by contract.
            return Uni.createFrom().item(toResponse(List.of(), startedAt));
        }
        List<Uni<HealthCheckResult>> results = checks.stream().map(HealthCheck::check).toList();

        return Uni.combine().all().unis(results)
                .with(HealthCheckResult.class, aggregate -> toResponse(aggregate, startedAt));
    }

    private Response toResponse(List<HealthCheckResult> results, Instant startedAt) {
        HealthStatus.Status status = HealthAggregator.aggregate(results);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.name());
        List<Map<String, Object>> checks = results.stream().map(result -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("checkId", result.checkId());
            entry.put("status", result.status().name());
            entry.put("message", result.message());
            entry.put("durationMs", result.duration().toMillis());
            return entry;
        }).toList();
        body.put("checks", checks);
        body.put("evaluatedAt", Instant.now().toString());
        body.put("durationMs", Duration.between(startedAt, Instant.now()).toMillis());

        return Response.status(status == HealthStatus.Status.UP ? 200 : 503).entity(body).build();
    }
}
