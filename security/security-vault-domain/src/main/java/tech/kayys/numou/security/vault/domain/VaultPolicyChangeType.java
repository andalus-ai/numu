package tech.kayys.numou.security.vault.domain;

/** Auditable reason for publishing a new immutable policy revision. */
public enum VaultPolicyChangeType {
    PUBLISH,
    ROLLBACK
}
