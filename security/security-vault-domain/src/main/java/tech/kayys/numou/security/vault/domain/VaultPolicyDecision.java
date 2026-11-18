package tech.kayys.numou.security.vault.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Non-sensitive policy result suitable for decision auditing. Revision zero means no policy published. */
public record VaultPolicyDecision(UUID tenantId, VaultOperation operation,
                                  VaultPolicyEffect effect, long policyVersion,
                                  Optional<UUID> matchedRuleId, String reasonCode,
                                  java.time.Instant evaluatedAt) {
    public VaultPolicyDecision {
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(operation, "operation is required");
        Objects.requireNonNull(effect, "effect is required");
        if (policyVersion < 0) throw new IllegalArgumentException("policyVersion must not be negative");
        matchedRuleId = matchedRuleId == null ? Optional.empty() : matchedRuleId;
        if (reasonCode == null || reasonCode.isBlank() || reasonCode.length() > 80)
            throw new IllegalArgumentException("reasonCode must contain 1..80 characters");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt is required");
    }
}
