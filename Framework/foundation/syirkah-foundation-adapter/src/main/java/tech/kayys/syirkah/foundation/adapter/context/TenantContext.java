package tech.kayys.syirkah.foundation.adapter.context;

import io.quarkus.arc.Arc;

/**
 * Static accessor kept for source compatibility with existing resources. Inside a request it reads and writes the
 * request-scoped {@link RequestContextHolder}; outside one (unit tests, startup code, blocking worker threads) it
 * falls back to a thread-local.
 *
 * <p>Behaviour change that matters: after the security filter has bound the tenant to the authenticated principal,
 * {@link #setTenantId} with a different value throws instead of silently switching tenants.
 */
public final class TenantContext {

    private static final ThreadLocal<String> FALLBACK = new ThreadLocal<>();

    private TenantContext() { }

    public static String getTenantId() {
        final var holder = holder();
        return holder != null ? holder.tenantId() : FALLBACK.get();
    }

    public static void setTenantId(String tenantId) {
        final var holder = holder();
        if (holder != null) {
            holder.unverifiedTenant(tenantId);
        } else {
            FALLBACK.set(tenantId);
        }
    }

    public static void clear() {
        FALLBACK.remove(); // request-scoped state is discarded with the request
    }

    static RequestContextHolder holder() {
        final var container = Arc.container();
        if (container == null || !container.requestContext().isActive()) {
            return null;
        }
        return container.instance(RequestContextHolder.class).get();
    }
}
