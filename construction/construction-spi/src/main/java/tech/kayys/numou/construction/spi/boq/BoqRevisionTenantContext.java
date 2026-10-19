package tech.kayys.numou.construction.spi.boq;

/**
 * Supplies the tenant selected by the authenticated server-side security context.
 * Implementations must fail closed when no tenant is established and must never
 * read tenant identity from command/request payload fields.
 *
 * The returned value must be a canonical tenant identifier matching the database
 * representation used by BOQ, revision, idempotency, and outbox persistence.
 */
@FunctionalInterface
public interface BoqRevisionTenantContext {
    String requireTenantId();
}
