package tech.kayys.numou.security.vault.domain;

/** Vault-local operations; deliberately separate from generic platform CRUD verbs. */
public enum VaultOperation {
    CREATE, READ, ROTATE, REVOKE
}
