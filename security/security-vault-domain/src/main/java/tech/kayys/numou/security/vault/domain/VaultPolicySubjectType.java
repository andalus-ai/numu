package tech.kayys.numou.security.vault.domain;

/** Stable subject namespaces; values are matched exactly, never interpreted as expressions. */
public enum VaultPolicySubjectType {
    PRINCIPAL_ID,
    ROLE,
    WORKLOAD_SUBJECT
}
