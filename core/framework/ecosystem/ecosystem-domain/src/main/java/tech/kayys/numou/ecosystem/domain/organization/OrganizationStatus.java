package tech.kayys.numou.ecosystem.domain.organization;

/**
 * Lifecycle of an organization's profile.
 *
 * <p>Dissolution is terminal. It does not delete the underlying Party
 * identity or historical relationships and transactions.
 */
public enum OrganizationStatus {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    DISSOLVED
}
