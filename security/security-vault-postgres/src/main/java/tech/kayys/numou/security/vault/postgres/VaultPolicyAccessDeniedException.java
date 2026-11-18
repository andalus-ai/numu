package tech.kayys.numou.security.vault.postgres;

/** Non-disclosing policy denial; detailed rule data remains in protected audit records. */
public final class VaultPolicyAccessDeniedException extends RuntimeException {
    public VaultPolicyAccessDeniedException() {
        super("Vault access denied by policy");
    }
}
