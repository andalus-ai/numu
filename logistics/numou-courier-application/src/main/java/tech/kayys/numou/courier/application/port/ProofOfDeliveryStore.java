package tech.kayys.numou.courier.application.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Tenant-explicit persistence boundary for immutable proof-of-delivery evidence. */
public interface ProofOfDeliveryStore {
    CompletionStage<Result> record(Command command);

    enum Outcome { ACCEPTED, REPLAYED, IDEMPOTENCY_CONFLICT }

    record Command(
            TenantId tenantId,
            UUID proofId,
            String shipmentReference,
            String deliveryAttemptReference,
            Instant deliveredAt,
            String recipientLabel,
            String evidenceReference,
            String evidenceSha256,
            String actorReference,
            String source,
            String idempotencyKey,
            String payloadFingerprint,
            Instant occurredAt) { }

    record Result(Outcome outcome, UUID proofId) { }
}
