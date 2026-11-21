package tech.kayys.numou.history.source.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only provenance for one extraction attempt. Extracted content is a separate shared-document
 * reference; this record never changes the original source or original binary.
 */
public record SourceExtractionArtifact(
    UUID id,
    UUID requestId,
    HistoricalSourceId sourceId,
    int attempt,
    SourceExtractionStatus status,
    String extractedDocumentRef,
    String extractedSha256,
    String extractedMediaType,
    String languageTag,
    String locator,
    String failureCode,
    String failureDetail,
    Instant startedAt,
    Instant completedAt
) {
    public SourceExtractionArtifact {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(sourceId, "sourceId");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
        Objects.requireNonNull(status, "status");
        extractedDocumentRef = optional(extractedDocumentRef, 1000, "extractedDocumentRef");
        extractedSha256 = optional(extractedSha256, 64, "extractedSha256");
        if (extractedSha256 != null) {
            extractedSha256 = extractedSha256.toLowerCase(Locale.ROOT);
            if (!extractedSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("extractedSha256 must be a 64-character hexadecimal SHA-256 digest");
        }
        extractedMediaType = optional(extractedMediaType, 200, "extractedMediaType");
        languageTag = optional(languageTag, 64, "languageTag");
        locator = optional(locator, 1000, "locator");
        failureCode = optional(failureCode, 100, "failureCode");
        failureDetail = optional(failureDetail, 2000, "failureDetail");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(startedAt)) throw new IllegalArgumentException("completedAt cannot precede startedAt");
        if (status == SourceExtractionStatus.FAILED) {
            if (extractedDocumentRef != null || extractedSha256 != null) throw new IllegalArgumentException("failed extraction cannot publish extracted content");
            if (failureCode == null) throw new IllegalArgumentException("failed extraction requires failureCode");
        } else {
            if (extractedDocumentRef == null || extractedSha256 == null || extractedMediaType == null) {
                throw new IllegalArgumentException("successful or partial extraction requires a content reference, digest and media type");
            }
            if (failureCode != null) throw new IllegalArgumentException("successful or partial extraction cannot have failureCode");
        }
    }

    public static SourceExtractionArtifact create(
        UUID requestId, HistoricalSourceId sourceId, int attempt, SourceExtractionStatus status,
        String extractedDocumentRef, String extractedSha256, String extractedMediaType,
        String languageTag, String locator, String failureCode, String failureDetail,
        Instant startedAt, Instant completedAt
    ) {
        return new SourceExtractionArtifact(UUID.randomUUID(), requestId, sourceId, attempt, status,
            extractedDocumentRef, extractedSha256, extractedMediaType, languageTag, locator,
            failureCode, failureDetail, startedAt, completedAt);
    }

    private static String optional(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return normalized;
    }
}
