package tech.kayys.numou.courier.domain;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable shipment milestone/custody fact. occurredAt is supplied by the scanner or
 * operational source; recordedAt is server-side ingestion time. Events are append-only.
 */
public record TrackingEvent(
        TrackingEventId id,
        TenantId tenantId,
        ShipmentId shipmentId,
        ParcelId parcelId,
        TrackingEventType type,
        Instant occurredAt,
        Instant recordedAt,
        String locationCode,
        String actorReference,
        String source,
        String idempotencyKey,
        String note) {

    public TrackingEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(shipmentId, "shipmentId must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        if (recordedAt.isBefore(occurredAt))
            throw new IllegalArgumentException("recordedAt must not be before occurredAt");
        locationCode = optionalNormalized(locationCode, 64, "locationCode");
        actorReference = optionalTrimmed(actorReference, 160, "actorReference");
        source = required(source, 64, "source").toUpperCase(Locale.ROOT);
        idempotencyKey = required(idempotencyKey, 160, "idempotencyKey");
        note = optionalTrimmed(note, 1000, "note");
    }

    private static String required(String value, int max, String field) {
        String result = Objects.requireNonNull(value, field + " must not be null").trim();
        if (result.isEmpty() || result.length() > max)
            throw new IllegalArgumentException(field + " must contain 1-" + max + " characters");
        return result;
    }

    private static String optionalTrimmed(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return result;
    }

    private static String optionalNormalized(String value, int max, String field) {
        String result = optionalTrimmed(value, max, field);
        return result == null ? null : result.toUpperCase(Locale.ROOT);
    }
}
