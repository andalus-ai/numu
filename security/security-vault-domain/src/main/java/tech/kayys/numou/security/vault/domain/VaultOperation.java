package tech.kayys.numou.security.vault.domain;

/** Vault-local operations and their canonical workload scope names. */
public enum VaultOperation {
    CREATE("secret:create"),
    READ("secret:read"),
    ROTATE("secret:rotate"),
    REVOKE("secret:revoke");

    private final String requiredScope;

    VaultOperation(String requiredScope) {
        this.requiredScope = requiredScope;
    }

    public String requiredScope() {
        return requiredScope;
    }
}
