package tech.kayys.numou.applicationplatform.application.installation;

import tech.kayys.numou.applicationplatform.domain.installation.ApplicationInstallationId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-scoped operational read and audited redrive persistence boundary. */
public interface ApplicationInstallationOperationsPort {
    CompletionStage<List<TransitionEntry>> history(TenantId tenantId, ApplicationInstallationId installationId, int limit);
    CompletionStage<List<DeadLetterEntry>> deadLetters(TenantId tenantId, int limit);
    CompletionStage<RedriveResult> redrive(RedriveCommand command);

    record TransitionEntry(UUID eventId, String fromStatus, String toStatus, long revision,
            String actorId, String correlationId, Instant occurredAt, String reasonCode) {}

    record DeadLetterEntry(UUID eventId, UUID installationId, String applicationId, String packageVersion,
            String failureCode, int attempts, Instant occurredAt) {}

    record RedriveCommand(TenantId tenantId, ApplicationInstallationId installationId,
            String actorId, String reason, String idempotencyKeySha256, Instant now) {
        public RedriveCommand {
            if (tenantId == null || installationId == null || now == null)
                throw new IllegalArgumentException("tenant, installation and time are required");
            if (actorId == null || actorId.isBlank() || actorId.length() > 200)
                throw new IllegalArgumentException("actorId is required");
            if (reason == null || reason.isBlank() || reason.trim().length() > 500)
                throw new IllegalArgumentException("redrive reason must contain 1 to 500 characters");
            if (idempotencyKeySha256 == null || !idempotencyKeySha256.matches("^[a-f0-9]{64}$"))
                throw new IllegalArgumentException("idempotency key must be lowercase SHA-256 hex");
        }
    }

    record RedriveResult(Outcome outcome, UUID redriveEventId, UUID operationId) {
        public enum Outcome { QUEUED, REPLAYED, NOT_REDRIVABLE, CONFLICT }
    }
}
