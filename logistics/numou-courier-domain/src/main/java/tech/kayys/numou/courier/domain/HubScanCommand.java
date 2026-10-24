package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable, tenant-scoped request to ingest an operational scan.
 *
 * The payload fingerprint is calculated by the trusted adapter from a canonical
 * representation of the request. The persistence boundary compares it when an
 * idempotency key is replayed: equal fingerprints are safe replays; unequal
 * fingerprints are key-reuse conflicts.
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
        Instant receivedAt) {

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
        if (receivedAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("receivedAt must not be before occurredAt");
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
