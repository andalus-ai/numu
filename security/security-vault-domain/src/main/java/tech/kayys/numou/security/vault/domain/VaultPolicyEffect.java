package tech.kayys.numou.security.vault.domain;

/** Explicit policy outcome. Deny always takes precedence over allow. */
public enum VaultPolicyEffect {
    ALLOW,
    DENY
}
