package tech.kayys.numou.history.source.domain;

import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable request. Document IDs are opaque references owned by the shared document capability. */
public record SourceIngestionRequest(
        UUID id,
        HistoricalSourceId sourceId,
        SourceIngestionInputKind inputKind,
        UUID inputDocumentId,
        UUID inputDocumentVersionId,
        String inputSha256,
        String inputContentType,
        String inputUri,
        String requestedBy,
        Instant requestedAt
) {
    private static final Pattern SHA256 = Pattern.compile("[a-fA-F0-9]{64}");

    public SourceIngestionRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(inputKind, "inputKind");
        requestedBy = required(requestedBy, "requestedBy", 256);
        Objects.requireNonNull(requestedAt, "requestedAt");

        if (inputKind == SourceIngestionInputKind.DOCUMENT_VERSION) {
            Objects.requireNonNull(inputDocumentId, "inputDocumentId");
            Objects.requireNonNull(inputDocumentVersionId, "inputDocumentVersionId");
            inputSha256 = checksum(inputSha256, "inputSha256");
            inputContentType = required(inputContentType, "inputContentType", 255);
            if (inputUri != null) throw new IllegalArgumentException("Document ingestion must not carry an input URI");
        } else {
            if (inputDocumentId != null || inputDocumentVersionId != null
                    || inputSha256 != null || inputContentType != null) {
                throw new IllegalArgumentException("URI ingestion must not carry document-version fields");
            }
            inputUri = validateHttpUri(inputUri);
        }
    }

    public static SourceIngestionRequest fromDocument(
            HistoricalSourceId sourceId, UUID documentId, UUID documentVersionId,
            String sha256, String contentType, String requestedBy, Instant requestedAt) {
        return new SourceIngestionRequest(UUID.randomUUID(), sourceId, SourceIngestionInputKind.DOCUMENT_VERSION,
                documentId, documentVersionId, sha256, contentType, null, requestedBy, requestedAt);
    }

    public static SourceIngestionRequest fromCanonicalUri(
            HistoricalSourceId sourceId, String inputUri, String requestedBy, Instant requestedAt) {
        return new SourceIngestionRequest(UUID.randomUUID(), sourceId, SourceIngestionInputKind.CANONICAL_URI,
                null, null, null, null, inputUri, requestedBy, requestedAt);
    }

    private static String validateHttpUri(String value) {
        String normalized = required(value, "inputUri", 2000);
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("inputUri is invalid", failure);
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null
                || scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("inputUri must be an absolute HTTP(S) URI without user-info");
        }
        return uri.toASCIIString();
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
}
