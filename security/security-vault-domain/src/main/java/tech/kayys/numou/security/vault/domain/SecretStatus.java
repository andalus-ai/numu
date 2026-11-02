package tech.kayys.numou.security.vault.domain;

/** Lifecycle state of a logical secret, not of an individual immutable version. */
public enum SecretStatus {
    ACTIVE,
    REVOKED
}
