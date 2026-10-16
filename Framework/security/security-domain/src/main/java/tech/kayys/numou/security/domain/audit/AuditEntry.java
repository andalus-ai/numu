package tech.kayys.syirkah.security.domain.audit;

import java.util.Objects;

/**
 * Business audit record (base01.md §P1-18).
 *
 * <p>Distinct from technical logging on purpose: this is evidence about
 * business decisions, retained for accounting, HR, compliance and
 * enterprise customers - not a debug message with a retention policy of
 * 14 days.
 *
 * @param actor         who acted
 * @param tenant        in which tenant
 * @param action        what was done, e.g. SHIPMENT_DISPATCHED
 * @param resource      the domain concept touched
 * @param resourceId    the instance touched
 * @param timestamp     when, as an ISO instant
 * @param correlationId the journey this belongs to
 * @param changes       optional before/after summary
 */
public record AuditEntry(
        String actor,
        String tenant,
        String action,
        String resource,
        String resourceId,
        String timestamp,
        String correlationId,
        String changes) {

    public AuditEntry {
        Objects.requireNonNull(actor, "actor cannot be null");
        Objects.requireNonNull(tenant, "tenant cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
        Objects.requireNonNull(resource, "resource cannot be null");
        Objects.requireNonNull(timestamp, "timestamp cannot be null");
        resourceId = resourceId == null ? "" : resourceId;
        correlationId = correlationId == null ? "" : correlationId;
        changes = changes == null ? "" : changes;
    }

    /** Convenience factory that fills in the current instant. */
    public static AuditEntry of(
            String actor,
            String tenant,
            String action,
            String resource,
            String resourceId,
            String correlationId) {
        return new AuditEntry(
                actor, tenant, action, resource, resourceId,
                java.time.Instant.now().toString(), correlationId, "");
    }
}
