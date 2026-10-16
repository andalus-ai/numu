package tech.kayys.syirkah.asset.domain.document;

import tech.kayys.syirkah.asset.domain.identifier.AssetId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Asset-side reference to a document owned by the reusable Documents
 * capability (ASSET-24 §4 / §13 / §15).
 *
 * <p>Never carries binary content — only the {@code documentId} (+ optional
 * pinned {@code documentVersion}) and the business classification relevant to
 * Asset. Tenant scoping is enforced structurally by the repository ports.</p>
 */
public record AssetDocumentReference(
        AssetDocumentReferenceId id,
        String tenantId,
        AssetId assetId,
        UUID documentId,
        Integer documentVersion,
        AssetDocumentType type,
        String title,
        boolean primary,
        boolean required,
        Instant validFrom,
        Instant validUntil,
        Instant linkedAt,
        String linkedBy
) {

    public AssetDocumentReference {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(assetId, "assetId cannot be null");
        Objects.requireNonNull(documentId, "documentId cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(title, "title cannot be null");
        Objects.requireNonNull(linkedAt, "linkedAt cannot be null");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title cannot be blank");
        }
        if (validFrom != null && validUntil != null && validUntil.isBefore(validFrom)) {
            throw new IllegalArgumentException("validUntil cannot be before validFrom");
        }
    }

    /** True when the reference carries an expiry that has passed. */
    public boolean expired(Instant now) {
        return validUntil != null && now.isAfter(validUntil);
    }

    /** True when the reference expires within {@code [now, now + window]}. */
    public boolean expiringWithin(Instant now, java.time.Duration window) {
        return validUntil != null
                && !validUntil.isBefore(now)
                && !validUntil.isAfter(now.plus(window));
    }
}
