package tech.kayys.numou.identity.adapter.inbound.rest;

import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.security.application.ratelimit.TokenBucketRateLimiter;

import java.util.Map;

/**
 * Applies a bounded per-source token bucket to public local-password login
 * endpoints before authentication. The socket peer address is used; forwarded
 * headers are deliberately not trusted here.
 *
 * This is a per-process control. Deployments with multiple replicas must also
 * enforce a shared/global limit at the trusted ingress or API gateway.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 100)
public class LoginRateLimitFilter implements ContainerRequestFilter {

    private static final String V1_LOGIN = "api/v1/auth/login";
    private static final String V2_LOGIN = "api/v2/auth/login";

    @Inject
    TokenBucketRateLimiter limiter;

    @Context
    RoutingContext routingContext;

    @ConfigProperty(name = "numou.identity.login-rate-limit.enabled", defaultValue = "true")
    boolean enabled;

    @Override
    public void filter(ContainerRequestContext request) {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) {
            return;
        }

        String path = request.getUriInfo().getPath();
        if (!V1_LOGIN.equals(path) && !V2_LOGIN.equals(path)) {
            return;
        }

        String source = "unknown";
        if (routingContext != null && routingContext.request() != null
                && routingContext.request().remoteAddress() != null) {
            source = routingContext.request().remoteAddress().host();
        }

        var decision = limiter.tryAcquire("identity-login:" + source + ":" + path);
        if (!decision.allowed()) {
            long retryAfter = Math.max(1, (long) Math.ceil(decision.retryAfter().toMillis() / 1000.0d));
            request.abortWith(Response.status(429)
                    .header("Retry-After", retryAfter)
                    .type("application/json")
                    .entity(Map.of(
                            "error", "rate_limited",
                            "message", "Too many login attempts. Retry later.",
                            "retryAfterSeconds", retryAfter))
                    .build());
        }
    }
}
