package tech.kayys.numou.applicationplatform.domain.installation;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ApplicationInstallationTransition(
        ApplicationInstallationId installationId,
        TenantId tenantId,
        ApplicationId applicationId,
        Optional<ApplicationInstallationStatus> fromStatus,
        ApplicationInstallationStatus toStatus,
        long revision,
        String actorId,
        String correlationId,
        Instant occurredAt,
        String reasonCode) {
    public ApplicationInstallationTransition {
        Objects.requireNonNull(installationId); Objects.requireNonNull(tenantId);
        Objects.requireNonNull(applicationId); Objects.requireNonNull(fromStatus);
        Objects.requireNonNull(toStatus); Objects.requireNonNull(actorId); Objects.requireNonNull(occurredAt);
        correlationId = correlationId == null ? "" : correlationId.trim();
        reasonCode = reasonCode == null ? "" : reasonCode.trim();
        if (actorId.isBlank() || actorId.length() > 200)
            throw new IllegalArgumentException("actorId must contain 1 to 200 characters");
        if (correlationId.length() > 200) throw new IllegalArgumentException("correlationId must not exceed 200 characters");
        if (reasonCode.length() > 120) throw new IllegalArgumentException("reasonCode must not exceed 120 characters");
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
    }
}
