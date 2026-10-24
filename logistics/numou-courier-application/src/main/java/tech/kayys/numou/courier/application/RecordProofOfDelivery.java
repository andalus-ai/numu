package tech.kayys.numou.courier.application;

import tech.kayys.numou.courier.application.port.ProofOfDeliveryStore;
import tech.kayys.numou.courier.application.port.ProofOfDeliveryStore.Command;
import tech.kayys.numou.courier.application.port.ProofOfDeliveryStore.Result;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/**
 * Records delivery evidence without directly advancing shipment state.
 * Tenant identity and evidence authorization must be established by trusted callers.
 */
public final class RecordProofOfDelivery {
    private static final Pattern SHA256 = Pattern.compile("(?i)[0-9a-f]{64}");
    private final ProofOfDeliveryStore store;

    public RecordProofOfDelivery(ProofOfDeliveryStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    public CompletionStage<Result> execute(Request request) {
        if (request == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("request must not be null"));
        final Request valid;
        try {
            valid = request.validated();
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        return store.record(new Command(valid.tenantId(), valid.proofId(), valid.shipmentReference(),
                valid.deliveryAttemptReference(), valid.deliveredAt(), valid.recipientLabel(),
                valid.evidenceReference(), valid.evidenceSha256().toLowerCase(java.util.Locale.ROOT),
                valid.actorReference(), valid.source(), valid.idempotencyKey(),
                valid.payloadFingerprint(), valid.occurredAt()));
    }

    public record Request(
            tech.kayys.numou.foundation.domain.tenant.TenantId tenantId,
            UUID proofId,
            String shipmentReference,
            String deliveryAttemptReference,
            java.time.Instant deliveredAt,
            String recipientLabel,
            String evidenceReference,
            String evidenceSha256,
            String actorReference,
            String source,
            String idempotencyKey,
            String payloadFingerprint,
            java.time.Instant occurredAt) {
        Request validated() {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(proofId, "proofId must not be null");
            Objects.requireNonNull(deliveredAt, "deliveredAt must not be null");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            if (deliveredAt.isAfter(occurredAt)) {
                throw new IllegalArgumentException("deliveredAt must not be after occurredAt");
            }
            String shipment = required(shipmentReference, 160, "shipmentReference");
            String attempt = required(deliveryAttemptReference, 160, "deliveryAttemptReference");
            String recipient = required(recipientLabel, 160, "recipientLabel");
            String evidence = required(evidenceReference, 500, "evidenceReference");
            String digest = required(evidenceSha256, 64, "evidenceSha256");
            if (!SHA256.matcher(digest).matches()) {
                throw new IllegalArgumentException("evidenceSha256 must be a 64-character SHA-256 hex digest");
            }
            String actor = required(actorReference, 160, "actorReference");
            String sourceValue = required(source, 64, "source");
            String key = required(idempotencyKey, 160, "idempotencyKey");
            String fingerprint = required(payloadFingerprint, 128, "payloadFingerprint");
            return new Request(tenantId, proofId, shipment, attempt, deliveredAt, recipient, evidence,
                    digest, actor, sourceValue, key, fingerprint, occurredAt);
        }

        private static String required(String value, int max, String field) {
            Objects.requireNonNull(value, field + " must not be null");
            String normalized = value.trim();
            if (normalized.isEmpty() || normalized.length() > max) {
                throw new IllegalArgumentException(field + " must contain 1-" + max + " characters");
            }
            return normalized;
        }
    }
}
