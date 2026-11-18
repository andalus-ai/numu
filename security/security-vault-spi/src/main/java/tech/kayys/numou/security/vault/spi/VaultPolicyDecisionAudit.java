package tech.kayys.numou.security.vault.spi;

import tech.kayys.numou.security.vault.domain.VaultPolicyDecision;
import tech.kayys.numou.security.vault.domain.VaultId;
import tech.kayys.numou.security.vault.domain.SecretId;

import java.util.Objects;
import java.util.UUID;

/** Metadata-only durable record of a policy decision. Never carries secret material or credentials. */
public record VaultPolicyDecisionAudit(UUID eventId, String subjectReference,
                                       VaultId vaultId, SecretId secretId,
                                       VaultPolicyDecision decision, String correlationId) {
    public VaultPolicyDecisionAudit {
        Objects.requireNonNull(eventId, "eventId is required");
        if (subjectReference == null || subjectReference.isBlank() || subjectReference.length() > 256)
            throw new IllegalArgumentException("subjectReference must contain 1..256 characters");
        Objects.requireNonNull(vaultId, "vaultId is required");
        Objects.requireNonNull(secretId, "secretId is required");
        Objects.requireNonNull(decision, "decision is required");
        if (correlationId != null && correlationId.length() > 160)
            throw new IllegalArgumentException("correlationId exceeds 160 characters");
    }
}
