package tech.kayys.numou.identity.adapter.inbound.observability;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Identity MDC context: puts {@code traceId}, {@code actorId}, {@code tenantId} and
 * {@code clientIp} on every log line of a request, and echoes {@code x-trace-id} back so a
 * support ticket can quote it.
 *
 * <p>Zero-exposure policy: this filter only ever handles identifiers - raw tokens, passwords and
 * signing keys must never reach MDC. Values are accepted only when they look sane, otherwise a
 * fresh id is generated rather than trusting caller input.
 */
@Provider
@Priority(Priorities.AUTHENTICATION - 300)
public class IdentityMdcFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String TRACE_ID_HEADER = "x-trace-id";
    public static final String ACTOR_HEADER = "X-Actor-Id";
    public static final String TENANT_HEADER = "X-Tenant-Id";

    private static final String START = "identity.mdc.traceId";
    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9._:-]{8,64}$");

    @Override
    public void filter(ContainerRequestContext request) {
        String traceId = sanitize(request.getHeaderString(TRACE_ID_HEADER));
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        request.setProperty(START, traceId);
        MDC.put("traceId", traceId);

        putIfSanitized("actorId", request.getHeaderString(ACTOR_HEADER));
        putIfSanitized("tenantId", request.getHeaderString(TENANT_HEADER));
        putIfSanitized("clientIp", clientIp(request));
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        Object traceId = request.getProperty(START);
        if (traceId instanceof String id) {
            response.getHeaders().putSingle(TRACE_ID_HEADER, id);
        }
        MDC.remove("traceId");
        MDC.remove("actorId");
        MDC.remove("tenantId");
        MDC.remove("clientIp");
    }

    private static void putIfSanitized(String key, String value) {
        String safe = sanitize(value);
        if (safe != null) {
            MDC.put(key, safe);
        }
    }

    private static String sanitize(String value) {
        return value != null && SAFE_VALUE.matcher(value).matches() ? value : null;
    }

    /** First hop of {@code X-Forwarded-For}, falling back to {@code X-Real-Ip}. */
    private static String clientIp(ContainerRequestContext request) {
        String forwarded = request.getHeaderString("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeaderString("X-Real-Ip");
        return realIp != null && !realIp.isBlank() ? realIp.trim() : null;
    }
}
