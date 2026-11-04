package tech.kayys.numou.security.vault.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, versioned policy rule. A null vaultId or secretId is an explicit
 * wildcard at that scope level; tenant is never wildcarded.
 */
public record VaultPolicyRule(UUID ruleId, UUID tenantId, Long policyVersion,
                              VaultPolicySubjectType subjectType, String subject,
                              String workloadIssuer, String workloadAudience,
                              VaultId vaultId, SecretId secretId, VaultOperation operation,
                              VaultPolicyEffect effect, Instant validFrom, Instant validUntil) {
    public VaultPolicyRule {
        Objects.requireNonNull(ruleId, "ruleId is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (policyVersion == null || policyVersion < 1)
            throw new IllegalArgumentException("policyVersion must be positive");
        Objects.requireNonNull(subjectType, "subjectType is required");
        subject = required(subject, "subject", 256);
        if (subjectType == VaultPolicySubjectType.WORKLOAD_SUBJECT) {
            workloadIssuer = required(workloadIssuer, "workloadIssuer", 256);
            workloadAudience = required(workloadAudience, "workloadAudience", 256);
        } else if (workloadIssuer != null || workloadAudience != null) {
            throw new IllegalArgumentException("workload issuer/audience are only valid for workload subjects");
        }
        Objects.requireNonNull(operation, "operation is required");
        Objects.requireNonNull(effect, "effect is required");
        Objects.requireNonNull(validFrom, "validFrom is required");
        if (validUntil != null && !validUntil.isAfter(validFrom))
            throw new IllegalArgumentException("validUntil must be after validFrom");
    }

    public boolean isEffectiveAt(Instant now) {
        return !now.isBefore(validFrom) && (validUntil == null || now.isBefore(validUntil));
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new IllegalArgumentException(field + " must contain 1.." + max + " characters");
        return value;
    }
}
