package tech.kayys.numou.security.vault.postgres;

/** Optimistic concurrency conflict while publishing a tenant policy revision. */
public final class VaultPolicyRevisionConflictException extends RuntimeException {
    public VaultPolicyRevisionConflictException(long expected, long actual) {
        super("Vault policy revision conflict: expected " + expected + " but active revision is " + actual);
    }
}
