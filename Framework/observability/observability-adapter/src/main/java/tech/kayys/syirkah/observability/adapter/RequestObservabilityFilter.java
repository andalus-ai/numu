package tech.kayys.syirkah.observability.adapter;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import tech.kayys.syirkah.foundation.adapter.context.CorrelationContext;
import tech.kayys.syirkah.foundation.adapter.context.RequestContextHolder;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * First filter in the chain: establishes the correlation id (accepted from {@code X-Correlation-ID} when it looks
 * sane, otherwise generated), a request id, the MDC fields every log line carries, and echoes both ids back so a
 * support ticket can quote them. Also writes one structured access-log line per request using the route
 * <em>template</em> (never the raw path/query, which can contain guest names or phone numbers).
 * NOT COMPILED in the authoring sandbox. Verify with an integration test that MDC fields survive Mutiny hops on your
 * Quarkus version.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 200)
public class RequestObservabilityFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger ACCESS = Logger.getLogger("syirkah.access");
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");
    private static final String START = "syirkah.start";

    @Inject
    RequestContextHolder holder;

    @Override
    public void filter(ContainerRequestContext ctx) {
        final String supplied = ctx.getHeaderString(CorrelationContext.HEADER_CORRELATION_ID);
        final String correlationId = supplied != null && SAFE_ID.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
        final String requestId = UUID.randomUUID().toString();
        holder.correlationId(correlationId);
        holder.requestId(requestId);
        MDC.put("correlationId", correlationId);
        MDC.put("requestId", requestId);
        ctx.setProperty(START, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        res.getHeaders().putSingle(CorrelationContext.HEADER_CORRELATION_ID, holder.correlationId());
        res.getHeaders().putSingle("X-Request-Id", holder.requestId());
        final Object start = req.getProperty(START);
        final long ms = start instanceof Long s ? (System.nanoTime() - s) / 1_000_000 : -1;
        ACCESS.infof("method=%s route=%s status=%d durationMs=%d", req.getMethod(), routeTemplate(req), res.getStatus(), ms);
    }

    private static String routeTemplate(ContainerRequestContext req) {
        // Matched templates look like /api/v1/reservations/{id}; fall back to a coarse bucket, never the raw path.
        final var matched = req.getUriInfo().getMatchedURIs();
        return matched == null || matched.isEmpty() ? "unmatched" : "/" + matched.get(matched.size() - 1).replaceAll("[0-9a-fA-F-]{32,36}", "{id}");
    }
}
