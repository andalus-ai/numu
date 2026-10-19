package tech.kayys.numou.tenancy.domain.membership;

/**
 * Membership lifecycle states.
 * <pre>
 * INVITED ──accept──► ACTIVE ──suspend──► SUSPENDED ──activate──► ACTIVE
 * ACTIVE / SUSPENDED ──remove──► REMOVED
 * </pre>
 */
public enum TenantMemberStatus {
    INVITED,
    ACTIVE,
    SUSPENDED,
    REMOVED
}
