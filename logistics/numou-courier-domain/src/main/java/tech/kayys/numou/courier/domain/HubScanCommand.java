package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable tenant-scoped request to ingest an operational scan.
 *
 * <p>Aggregate references and custody evidence are explicit so a persistence adapter can
 * lock and validate the relevant container/wave in the same transaction as the receipt,
 * tracking fact, and outbox message.</p>
 */
public record HubScanCommand(
        TenantId tenantId,
        UUID commandId,
        String source,
        String idempotencyKey,
        String payloadFingerprint,
        ShipmentId shipmentId,
        String parcelReference,
        HubScanType type,
        String hubReference,
        String actorReference,
        Instant occurredAt,
        Instant receivedAt,
        UUID containerId,
        UUID dispatchWaveId,
        String sealReference,
        String manifestReference,
        String observedSealReference,
        String discrepancyReason) {

    /** Compatibility constructor for scans that do not imply container/wave transitions. */
    public HubScanCommand(TenantId tenantId, UUID commandId, String source, String idempotencyKey,
                          String payloadFingerprint, ShipmentId shipmentId, String parcelReference,
                          HubScanType type, String hubReference, String actorReference,
                          Instant occurredAt, Instant receivedAt) {
        this(tenantId, commandId, source, idempotencyKey, payloadFingerprint, shipmentId,
                parcelReference, type, hubReference, actorReference, occurredAt, receivedAt,
                null, null, null, null, null, null);
    }

    public HubScanCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(commandId, "commandId must not be null");
        source = required(source, 64, "source").toUpperCase(Locale.ROOT);
        idempotencyKey = required(idempotencyKey, 160, "idempotencyKey");
        payloadFingerprint = required(payloadFingerprint, 128, "payloadFingerprint");
        Objects.requireNonNull(shipmentId, "shipmentId must not be null");
        parcelReference = required(parcelReference, 160, "parcelReference");
        Objects.requireNonNull(type, "type must not be null");
        hubReference = required(hubReference, 160, "hubReference").toUpperCase(Locale.ROOT);
        actorReference = optional(actorReference, 160, "actorReference");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        sealReference = optional(sealReference, 160, "sealReference");
        manifestReference = optional(manifestReference, 160, "manifestReference");
        observedSealReference = optional(observedSealReference, 160, "observedSealReference");
        discrepancyReason = optional(discrepancyReason, 500, "discrepancyReason");
        if (receivedAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("receivedAt must not be before occurredAt");
        }
        switch (type) {
            case DISPATCH_LOADED -> Objects.requireNonNull(dispatchWaveId,
                    "dispatchWaveId is required for DISPATCH_LOADED");
            case CONTAINER_SEALED -> {
                Objects.requireNonNull(containerId, "containerId is required for CONTAINER_SEALED");
                required(sealReference, 160, "sealReference");
            }
            case CONTAINER_DISPATCHED -> {
                Objects.requireNonNull(containerId, "containerId is required for CONTAINER_DISPATCHED");
                Objects.requireNonNull(dispatchWaveId, "dispatchWaveId is required for CONTAINER_DISPATCHED");
                required(manifestReference, 160, "manifestReference");
            }
            case CONTAINER_RECEIVED -> {
                Objects.requireNonNull(containerId, "containerId is required for CONTAINER_RECEIVED");
                required(observedSealReference, 160, "observedSealReference");
            }
            case LOAD_DISCREPANCY -> required(discrepancyReason, 500, "discrepancyReason");
            case PICKUP_COLLECTED, HUB_INTAKE, SORTED, HUB_RECEIVED, EXCEPTION_RECORDED -> {
                // No mandatory aggregate reference for these scan types.
            }
        }
    }

    private static String required(String value, int max, String field) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " must contain 1-" + max + " characters");
        }
        return normalized;
    }

    private static String optional(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        return required(value, max, field);
    }
}
