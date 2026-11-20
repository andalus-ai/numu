package tech.kayys.numou.history.source.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable instruction to derive research content from an existing shared-document reference.
 * The referenced original is never copied into or replaced by the History capability.
 */
public record SourceIngestionRequest(
    UUID id,
    HistoricalSourceId sourceId,
    String idempotencyKey,
    String originalDocumentRef,
    String originalSha256,
    String mediaType,
    String extractorId,
    String extractorVersion,
    String configurationFingerprint,
    String requestedBy,
    Instant requestedAt
) {
    public SourceIngestionRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceId, "sourceId");
        idempotencyKey = required(idempotencyKey, "idempotencyKey", 200);
        originalDocumentRef = required(originalDocumentRef, "originalDocumentRef", 1000);
        originalSha256 = sha256(originalSha256);
        mediaType = required(mediaType, "mediaType", 200).toLowerCase(java.util.Locale.ROOT);
        extractorId = required(extractorId, "extractorId", 200);
        extractorVersion = required(extractorVersion, "extractorVersion", 100);
        configurationFingerprint = required(configurationFingerprint, "configurationFingerprint", 200);
        requestedBy = required(requestedBy, "requestedBy", 200);
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    public static SourceIngestionRequest create(
        HistoricalSourceId sourceId, String idempotencyKey, String originalDocumentRef,
        String originalSha256, String mediaType, String extractorId, String extractorVersion,
        String configurationFingerprint, String requestedBy, Instant requestedAt
    ) {
        return new SourceIngestionRequest(UUID.randomUUID(), sourceId, idempotencyKey,
            originalDocumentRef, originalSha256, mediaType, extractorId, extractorVersion,
            configurationFingerprint, requestedBy, requestedAt);
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return normalized;
    }

    private static String sha256(String value) {
        String normalized = required(value, "originalSha256", 64).toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("originalSha256 must be a 64-character hexadecimal SHA-256 digest");
        return normalized;
    }
}
