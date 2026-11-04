package tech.kayys.numou.security.vault.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable published policy snapshot. Revision is tenant-local and monotonically increasing. */
public record VaultPolicySnapshot(UUID tenantId, long revision, List<VaultPolicyRule> rules) {
    public VaultPolicySnapshot {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules are required"));
        if (rules.stream().anyMatch(rule -> !tenantId.equals(rule.tenantId())
                || rule.policyVersion() != revision))
            throw new IllegalArgumentException("every rule must belong to the snapshot tenant and revision");
        if (rules.stream().map(VaultPolicyRule::ruleId).distinct().count() != rules.size())
            throw new IllegalArgumentException("rule IDs must be unique within a snapshot");
    }
}
