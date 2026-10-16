package tech.kayys.syirkah.accounting.domain.document;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;

import java.time.Instant;
import java.util.Objects;

public final class UploadSession extends AbstractAggregateRoot<UploadSessionId> {
    private final String tenantId;
    private final String storageKey;
    private final DocumentType documentType;
    private final DocumentClassification classification;
    private final DocumentMetadata metadata;
    private final long expectedSize;
    private final String expectedSha256;
    private final Instant expiresAt;
    private UploadSessionStatus status;
    private DocumentId completedDocumentId;
    private DocumentVersionId completedVersionId;

    private UploadSession(
            UploadSessionId id,
            String tenantId,
            String storageKey,
            DocumentType documentType,
            DocumentClassification classification,
            DocumentMetadata metadata,
            long expectedSize,
            String expectedSha256,
            Instant expiresAt
    ) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.tenantId = requireText(tenantId, "tenantId");
        this.storageKey = requireText(storageKey, "storageKey");
        this.documentType = Objects.requireNonNull(documentType, "documentType cannot be null");
        this.classification = Objects.requireNonNull(classification, "classification cannot be null");
        this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
        if (expectedSize < 0 || expectedSize != metadata.fileSize()) {
            throw new IllegalArgumentException("expectedSize must match metadata file size");
        }
        this.expectedSize = expectedSize;
        this.expectedSha256 = normalizeChecksum(expectedSha256);
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        this.status = UploadSessionStatus.PENDING;
    }

    public static UploadSession create(
            UploadSessionId id,
            String tenantId,
            String storageKey,
            DocumentType documentType,
            DocumentClassification classification,
            DocumentMetadata metadata,
            String expectedSha256,
            Instant expiresAt
    ) {
        return new UploadSession(
                id, tenantId, storageKey, documentType, classification, metadata,
                metadata.fileSize(), expectedSha256, expiresAt
        );
    }

    public static UploadSession reconstitute(
            UploadSessionId id,
            String tenantId,
            String storageKey,
            DocumentType documentType,
            DocumentClassification classification,
            DocumentMetadata metadata,
            long expectedSize,
            String expectedSha256,
            Instant expiresAt,
            UploadSessionStatus status,
            DocumentId completedDocumentId,
            DocumentVersionId completedVersionId
    ) {
        var session = new UploadSession(
                id, tenantId, storageKey, documentType, classification, metadata,
                expectedSize, expectedSha256, expiresAt
        );
        session.status = Objects.requireNonNull(status, "status cannot be null");
        if (status == UploadSessionStatus.COMPLETED
                && (completedDocumentId == null || completedVersionId == null)) {
            throw new IllegalArgumentException("Completed upload sessions must reference their document version");
        }
        if (status != UploadSessionStatus.COMPLETED
                && (completedDocumentId != null || completedVersionId != null)) {
            throw new IllegalArgumentException("Only completed upload sessions may reference a document version");
        }
        session.completedDocumentId = completedDocumentId;
        session.completedVersionId = completedVersionId;
        return session;
    }

    public boolean isExpiredAt(Instant now) {
        return !Objects.requireNonNull(now, "now cannot be null").isBefore(expiresAt);
    }

    public void complete(DocumentId documentId, DocumentVersionId versionId, Instant now) {
        Objects.requireNonNull(now, "now cannot be null");
        if (status == UploadSessionStatus.COMPLETED) {
            if (completedDocumentId.equals(documentId) && completedVersionId.equals(versionId)) {
                return;
            }
            throw new IllegalStateException("Upload session was already completed");
        }
        if (status != UploadSessionStatus.PENDING) {
            throw new IllegalStateException("Cannot complete upload session in state " + status);
        }
        if (isExpiredAt(now)) {
            status = UploadSessionStatus.EXPIRED;
            throw new IllegalStateException("Upload session has expired");
        }
        status = UploadSessionStatus.COMPLETED;
        completedDocumentId = Objects.requireNonNull(documentId, "documentId cannot be null");
        completedVersionId = Objects.requireNonNull(versionId, "versionId cannot be null");
    }

    public void reject() {
        if (status != UploadSessionStatus.PENDING) {
            throw new IllegalStateException("Only pending upload sessions can be rejected");
        }
        status = UploadSessionStatus.REJECTED;
    }

    public String tenantId() { return tenantId; }
    public String storageKey() { return storageKey; }
    public DocumentType documentType() { return documentType; }
    public DocumentClassification classification() { return classification; }
    public DocumentMetadata metadata() { return metadata; }
    public long expectedSize() { return expectedSize; }
    public String expectedSha256() { return expectedSha256; }
    public Instant expiresAt() { return expiresAt; }
    public UploadSessionStatus status() { return status; }
    public DocumentId completedDocumentId() { return completedDocumentId; }
    public DocumentVersionId completedVersionId() { return completedVersionId; }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be blank");
        }
        return value;
    }

    private static String normalizeChecksum(String checksum) {
        if (checksum == null || checksum.isBlank()) {
            return null;
        }
        var normalized = checksum.toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("expectedSha256 must be a 64-character hexadecimal digest");
        }
        return normalized;
    }
}
