package tech.kayys.syirkah.security.adapter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import tech.kayys.syirkah.security.application.problem.Problem;
import tech.kayys.syirkah.security.application.ratelimit.TokenBucketRateLimiter;

import java.util.Map;

/**
 * Per-(tenant, principal) token bucket, applied after the tenant is resolved so one noisy tenant cannot exhaust
 * another's budget. In-process: with N replicas the effective limit is up to N times higher; enforce a hard global
 * limit at the gateway if you need one. NOT COMPILED in the authoring sandbox.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 200)
public class RateLimitFilter implements ContainerRequestFilter {

    @Inject
    TokenBucketRateLimiter limiter;
    @Inject
    CurrentSecurity current;
    @Inject
    SecurityConfig config;

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (!config.ratelimit().enabled() || !isProtected(ctx)) {
            return;
        }
        final var security = current.require();
        final var decision = limiter.tryAcquire(security.tenantId() + ":" + security.principal().id());
        if (!decision.allowed()) {
            final long retry = Math.max(1, decision.retryAfter().toSeconds());
            final var response = ProblemResponses.of(new Problem(429, "rate-limited", "Too many requests",
                    "Slow down and retry shortly.", Map.of("retryAfterSeconds", retry)));
            response.getHeaders().add("Retry-After", String.valueOf(retry));
            ctx.abortWith(response);
        }
    }

    private boolean isProtected(ContainerRequestContext ctx) {
        final String path = "/" + ctx.getUriInfo().getPath();
        return config.security().protectedPrefixes().stream().anyMatch(path::startsWith);
    }
}
