package tech.kayys.numou.security.vault.application;

/** Deliberately does not disclose which vault policy condition denied access. */
public final class VaultAccessDeniedException extends RuntimeException {
    public VaultAccessDeniedException() {
        super("Vault access denied");
    }
}
