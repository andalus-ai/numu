package tech.kayys.syirkah.tenancy.domain.tenant;

/**
 * Tenant lifecycle states.
 * <pre>
 * PROVISIONING ──activate──► ACTIVE ──suspend──► SUSPENDED ──activate──► ACTIVE
 *      ACTIVE / SUSPENDED ──deactivate──► INACTIVE
 * </pre>
 */
public enum TenantStatus {
    /** Initial state: being set up, not yet usable. */
    PROVISIONING,
    /** Fully operational. */
    ACTIVE,
    /** Temporarily disabled — can be reactivated. */
    SUSPENDED,
    /** Permanently disabled. */
    INACTIVE
}
