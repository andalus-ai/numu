package tech.kayys.syirkah.accounting.domain.document;

import java.time.Instant;
import java.util.Objects;

/** Immutable revision snapshot of a document's binary payload. */
public record DocumentVersion(
        DocumentVersionId id,
        int versionNumber,
        String sha256Hash,
        String storageKey,
        Instant createdAt
) {
    public DocumentVersion {
        Objects.requireNonNull(id);
        Objects.requireNonNull(sha256Hash);
        Objects.requireNonNull(storageKey);
        Objects.requireNonNull(createdAt);
        if (sha256Hash.isBlank() || storageKey.isBlank()) {
            throw new IllegalArgumentException("sha256Hash and storageKey must not be blank");
        }
        if (versionNumber < 1) throw new IllegalArgumentException("versionNumber must be >= 1");
    }
}
