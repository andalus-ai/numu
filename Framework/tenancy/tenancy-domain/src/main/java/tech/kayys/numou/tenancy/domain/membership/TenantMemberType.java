package tech.kayys.syirkah.tenancy.domain.membership;

/**
 * Describes the nature of a membership relationship — not authorization roles.
 * Fine-grained permissions (HR_MANAGER, CASHIER, etc.) belong to the
 * Authorization bounded context.
 */
public enum TenantMemberType {
    /** Account owner — highest trust level, typically maps to a legal owner. */
    OWNER,
    /** Platform administrator for this tenant. */
    ADMIN,
    /** Standard member with no special platform role. */
    MEMBER,
    /** Operational user (e.g., store operator). */
    OPERATOR,
    /** External party (contractor, consultant, partner). */
    EXTERNAL,
    /** Machine/service account. */
    SERVICE
}
