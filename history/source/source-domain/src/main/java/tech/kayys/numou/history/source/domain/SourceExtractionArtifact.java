package tech.kayys.numou.history.source.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable provenance for extracted content. The extracted payload itself lives in
 * the shared document capability; History stores only its stable document-version reference.
 */
public record SourceExtractionArtifact(
        UUID id,
        UUID ingestionId,
        SourceExtractionKind kind,
        String extractorId,
        String extractorVersion,
        UUID outputDocumentId,
        UUID outputDocumentVersionId,
        String inputSha256,
        String outputSha256,
        String languageTag,
        String locator,
        String workerRef,
        Instant extractedAt
) {
    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");

    public SourceExtractionArtifact {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ingestionId, "ingestionId");
        Objects.requireNonNull(kind, "kind");
        extractorId = required(extractorId, "extractorId", 200);
        extractorVersion = required(extractorVersion, "extractorVersion", 100);
        Objects.requireNonNull(outputDocumentId, "outputDocumentId");
        Objects.requireNonNull(outputDocumentVersionId, "outputDocumentVersionId");
        inputSha256 = checksum(inputSha256, "inputSha256");
        outputSha256 = checksum(outputSha256, "outputSha256");
        languageTag = optional(languageTag, 64);
        if (languageTag != null) {
            var locale = Locale.forLanguageTag(languageTag);
            if (locale.getLanguage().isBlank() || !locale.toLanguageTag().equalsIgnoreCase(languageTag)) {
                throw new IllegalArgumentException("languageTag must be a valid BCP 47 language tag");
            }
            languageTag = locale.toLanguageTag();
        }
        locator = optional(locator, 1000);
        workerRef = required(workerRef, "workerRef", 256);
        Objects.requireNonNull(extractedAt, "extractedAt");
    }

    public static SourceExtractionArtifact create(
            UUID ingestionId, SourceExtractionKind kind, String extractorId, String extractorVersion,
            UUID outputDocumentId, UUID outputDocumentVersionId, String inputSha256, String outputSha256,
            String languageTag, String locator, String workerRef, Instant extractedAt) {
        return new SourceExtractionArtifact(UUID.randomUUID(), ingestionId, kind, extractorId, extractorVersion,
                outputDocumentId, outputDocumentVersionId, inputSha256, outputSha256,
                languageTag, locator, workerRef, extractedAt);
    }

    private static String checksum(String value, String field) {
        String normalized = required(value, field, 64).toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a 64-character SHA-256 hex digest");
        }
        return normalized;
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters");
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("optional value exceeds " + max + " characters");
        return normalized;
    }
}
