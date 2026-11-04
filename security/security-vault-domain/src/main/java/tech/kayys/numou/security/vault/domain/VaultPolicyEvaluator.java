package tech.kayys.numou.security.vault.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Deterministic default-deny evaluator over one immutable policy snapshot. */
public final class VaultPolicyEvaluator {
    public VaultPolicyDecision evaluate(UUID tenantId, UUID principalId, Set<String> roles,
            VaultWorkloadIdentity workloadIdentity, VaultId vaultId, SecretId secretId,
            VaultOperation operation, List<VaultPolicyRule> rules, Instant now) {
        long revision = rules == null ? 0 : rules.stream()
                .mapToLong(VaultPolicyRule::policyVersion).max().orElse(0);
        return evaluate(tenantId, principalId, roles, workloadIdentity, vaultId, secretId,
                operation, rules, revision, now);
    }

    public VaultPolicyDecision evaluate(UUID tenantId, UUID principalId, Set<String> roles,
            VaultWorkloadIdentity workloadIdentity, VaultId vaultId, SecretId secretId,
            VaultOperation operation, List<VaultPolicyRule> rules, long snapshotRevision, Instant now) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(principalId, "principalId is required");
        Objects.requireNonNull(vaultId, "vaultId is required");
        Objects.requireNonNull(secretId, "secretId is required");
        Objects.requireNonNull(operation, "operation is required");
        Objects.requireNonNull(rules, "rules is required");
        Objects.requireNonNull(now, "now is required");
        if (snapshotRevision < 0) throw new IllegalArgumentException("snapshotRevision must not be negative");

        Set<String> safeRoles = roles == null ? Set.of() : Set.copyOf(roles);
        List<VaultPolicyRule> applicable = rules.stream()
                .filter(rule -> rule.tenantId().equals(tenantId))
                .filter(rule -> rule.policyVersion() == snapshotRevision)
                .filter(rule -> rule.operation() == operation)
                .filter(rule -> rule.vaultId() == null || rule.vaultId().equals(vaultId))
                .filter(rule -> rule.secretId() == null || rule.secretId().equals(secretId))
                .filter(rule -> rule.isEffectiveAt(now))
                .filter(rule -> matchesSubject(rule, tenantId, principalId, safeRoles, workloadIdentity, now))
                .sorted(Comparator.comparing(rule -> rule.ruleId().toString()))
                .toList();

        Optional<VaultPolicyRule> deny = applicable.stream()
                .filter(rule -> rule.effect() == VaultPolicyEffect.DENY).findFirst();
        Optional<VaultPolicyRule> allow = applicable.stream()
                .filter(rule -> rule.effect() == VaultPolicyEffect.ALLOW).findFirst();
        Optional<VaultPolicyRule> matched = deny.isPresent() ? deny : allow;
        VaultPolicyEffect effect = deny.isPresent() || allow.isEmpty()
                ? VaultPolicyEffect.DENY : VaultPolicyEffect.ALLOW;
        String reason = deny.isPresent() ? "EXPLICIT_DENY"
                : allow.isPresent() ? "MATCHED_ALLOW" : "NO_MATCH_DEFAULT_DENY";
        return new VaultPolicyDecision(tenantId, operation, effect, snapshotRevision,
                matched.map(VaultPolicyRule::ruleId), reason, now);
    }

    private boolean matchesSubject(VaultPolicyRule rule, UUID tenantId, UUID principalId, Set<String> roles,
                                   VaultWorkloadIdentity identity, Instant now) {
        return switch (rule.subjectType()) {
            case PRINCIPAL_ID -> principalId.toString().equals(rule.subject());
            case ROLE -> roles.contains(rule.subject());
            case WORKLOAD_SUBJECT -> identity != null
                    && identity.subject().equals(rule.subject())
                    && identity.permits(tenantId, rule.workloadIssuer(),
                            rule.workloadAudience(), rule.operation(), now);
        };
    }
}
