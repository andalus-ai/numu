package tech.kayys.syirkah.foundation.adapter.context;

import jakarta.enterprise.context.RequestScoped;

/**
 * Per-request state: tenant, correlation id, request id, actor.
 *
 * <p><b>Why this exists:</b> in a reactive Quarkus service one event-loop thread interleaves many requests. A
 * {@code ThreadLocal} set by request A is overwritten by request B while A awaits I/O, so A's next read returns
 * <em>B's tenant</em>. A request-scoped bean is bound to the request's Vert.x duplicated context instead, which is
 * what Mutiny and Hibernate Reactive propagate along the call chain.
 *
 * <p>The tenant, once marked {@code verified} (derived from the validated token), can no longer be changed by
 * application code. NOT COMPILED in the authoring sandbox.
 */
@RequestScoped
public class RequestContextHolder {

    private String tenantId;
    private boolean tenantVerified;
    private String correlationId;
    private String requestId;
    private String actor;

    public String tenantId() {
        return tenantId;
    }

    public boolean tenantVerified() {
        return tenantVerified;
    }

    /** Called only by the security filter after the token and tenant membership were validated. */
    public void verifiedTenant(String tenantId) {
        this.tenantId = tenantId;
        this.tenantVerified = true;
    }

    /** Legacy path ({@link TenantContext#setTenantId}). Refuses to overwrite a verified tenant with a different value. */
    public void unverifiedTenant(String tenantId) {
        if (tenantVerified && !java.util.Objects.equals(this.tenantId, tenantId)) {
            throw new SecurityException("Tenant is bound to the authenticated principal and cannot be changed");
        }
        if (!tenantVerified) {
            this.tenantId = tenantId;
        }
    }

    public String correlationId() {
        return correlationId;
    }

    public void correlationId(String value) {
        this.correlationId = value;
    }

    public String requestId() {
        return requestId;
    }

    public void requestId(String value) {
        this.requestId = value;
    }

    public String actor() {
        return actor;
    }

    public void actor(String value) {
        this.actor = value;
    }
}
