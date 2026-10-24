package tech.kayys.numou.courier.application;

import tech.kayys.numou.courier.application.port.DispatchWaveDiscrepancyResolutionAuthorizer;
import tech.kayys.numou.courier.application.port.DispatchWaveDiscrepancyResolutionStore;
import tech.kayys.numou.courier.application.port.DispatchWaveDiscrepancyResolutionStore.Command;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Permission-checked use case for resolving a durable dispatch-wave discrepancy.
 * Authorization evidence is obtained from a trusted port, never from request input.
 */
public final class ResolveDispatchWaveDiscrepancy {
    private final DispatchWaveDiscrepancyResolutionAuthorizer authorizer;
    private final DispatchWaveDiscrepancyResolutionStore store;

    public ResolveDispatchWaveDiscrepancy(
            DispatchWaveDiscrepancyResolutionAuthorizer authorizer,
            DispatchWaveDiscrepancyResolutionStore store) {
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    public CompletionStage<DispatchWaveDiscrepancyResolutionStore.Result> execute(Request request) {
        if (request == null) return CompletableFuture.failedFuture(
                new IllegalArgumentException("request must not be null"));
        final Request valid;
        try {
            valid = request.validated();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        DispatchWaveDiscrepancyResolutionAuthorizer.Request authorization =
                new DispatchWaveDiscrepancyResolutionAuthorizer.Request(
                        valid.tenantId(), valid.actorReference(), valid.waveId(), valid.resolutionCode());
        return authorizer.authorize(authorization).thenCompose(decision -> {
            if (decision == null || !decision.allowed()
                    || decision.decisionReference() == null || decision.decisionReference().isBlank()) {
                return CompletableFuture.failedFuture(
                        new SecurityException("actor is not authorized to resolve this dispatch-wave discrepancy"));
            }
            return store.resolve(new Command(valid.tenantId(), valid.resolutionId(), valid.waveId(),
                    valid.discrepancyId(), valid.parcelReference(), valid.resolutionCode(), valid.reason(),
                    valid.actorReference(), decision.decisionReference().trim(), valid.evidenceReference(),
                    valid.source(), valid.idempotencyKey(), valid.payloadFingerprint(), valid.occurredAt()));
        });
    }

    public record Request(
            TenantId tenantId,
            UUID resolutionId,
            UUID waveId,
            UUID discrepancyId,
            String parcelReference,
            String resolutionCode,
            String reason,
            String actorReference,
            String evidenceReference,
            String source,
            String idempotencyKey,
            String payloadFingerprint,
            Instant occurredAt) {
        Request validated() {
            Objects.requireNonNull(tenantId, "tenantId must not be null");
            Objects.requireNonNull(resolutionId, "resolutionId must not be null");
            Objects.requireNonNull(waveId, "waveId must not be null");
            parcelReference = required(parcelReference, 160, "parcelReference");
            resolutionCode = required(resolutionCode, 32, "resolutionCode");
            if (!java.util.Set.of("ACCEPT_UNEXPECTED", "ACKNOWLEDGE_DUPLICATE",
                    "WAIVE_MISSING", "REMOVE_FROM_MANIFEST").contains(resolutionCode)) {
                throw new IllegalArgumentException("unsupported resolutionCode");
            }
            reason = required(reason, 500, "reason");
            actorReference = required(actorReference, 160, "actorReference");
            evidenceReference = optional(evidenceReference, 500, "evidenceReference");
            source = required(source, 64, "source");
            idempotencyKey = required(idempotencyKey, 160, "idempotencyKey");
            payloadFingerprint = required(payloadFingerprint, 128, "payloadFingerprint");
            Objects.requireNonNull(occurredAt, "occurredAt must not be null");
            if (discrepancyId == null && !("WAIVE_MISSING".equals(resolutionCode)
                    || "REMOVE_FROM_MANIFEST".equals(resolutionCode))) {
                throw new IllegalArgumentException("discrepancyId is required for this resolutionCode");
            }
            return this;
        }

        private static String required(String value, int max, String field) {
            Objects.requireNonNull(value, field + " must not be null");
            String normalized = value.trim();
            if (normalized.isEmpty() || normalized.length() > max) {
                throw new IllegalArgumentException(field + " must contain 1-" + max + " characters");
            }
            return normalized;
        }
        private static String optional(String value, int max, String field) {
            return value == null || value.isBlank() ? null : required(value, max, field);
        }
    }
}