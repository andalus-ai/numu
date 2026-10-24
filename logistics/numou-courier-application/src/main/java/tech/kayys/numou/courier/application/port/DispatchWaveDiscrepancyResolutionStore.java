package tech.kayys.numou.courier.application.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-explicit persistence boundary for immutable load-discrepancy resolutions. */
public interface DispatchWaveDiscrepancyResolutionStore {
    CompletionStage<Result> resolve(Command command);

    enum Outcome { ACCEPTED, REPLAYED, IDEMPOTENCY_CONFLICT }

    record Command(
            TenantId tenantId,
            UUID resolutionId,
            UUID waveId,
            UUID discrepancyId,
            String parcelReference,
            String resolutionCode,
            String reason,
            String actorReference,
            String permissionReference,
            String evidenceReference,
            String source,
            String idempotencyKey,
            String payloadFingerprint,
            Instant occurredAt) { }

    record Result(Outcome outcome, String resolutionReference) { }
}