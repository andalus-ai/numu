package tech.kayys.syirkah.identity.domain.user;

/**
 * Deliberately simple - three states cover registration through
 * deactivation. Anything richer (locked-out, suspended-for-review,
 * pending-deletion) should be added only when a real use case needs
 * it, not speculatively.
 */
public enum UserStatus {

    PENDING_VERIFICATION,
    ACTIVE,
    DEACTIVATED

}
