package tech.kayys.syirkah.accounting.domain.document;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Root aggregate representing an enterprise document and its version history.
 */
public final class Document extends AbstractAggregateRoot<DocumentId> {

    private final DocumentType type;
    private final DocumentClassification classification;
    private DocumentStatus status;
    private DocumentMetadata metadata;
    private final List<DocumentVersion> versions = new ArrayList<>();

    public Document(DocumentId id, DocumentType type, DocumentClassification classification,
                    DocumentMetadata metadata, String initialHash, String initialStorageKey) {
        this(id, type, classification, metadata, initialHash, initialStorageKey, Instant.now());
    }

    public Document(DocumentId id, DocumentType type, DocumentClassification classification,
                    DocumentMetadata metadata, String initialHash, String initialStorageKey, Instant occurredAt) {
        super(Objects.requireNonNull(id));
        this.type = Objects.requireNonNull(type);
        this.classification = Objects.requireNonNull(classification);
        this.metadata = Objects.requireNonNull(metadata);
        this.status = DocumentStatus.ACTIVE;

        DocumentVersion initialVersion = new DocumentVersion(
                DocumentVersionId.generate(), 1, initialHash, initialStorageKey, Objects.requireNonNull(occurredAt));
        this.versions.add(initialVersion);
        raise(new DocumentCreated(id, occurredAt, initialVersion.id()));
    }

    private Document(
            DocumentId id,
            DocumentType type,
            DocumentClassification classification,
            DocumentStatus status,
            DocumentMetadata metadata,
            List<DocumentVersion> versions
    ) {
        super(Objects.requireNonNull(id));
        this.type = Objects.requireNonNull(type);
        this.classification = Objects.requireNonNull(classification);
        this.status = Objects.requireNonNull(status);
        this.metadata = Objects.requireNonNull(metadata);
        var ordered = versions.stream()
                .sorted(Comparator.comparingInt(DocumentVersion::versionNumber))
                .toList();
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("A persisted document must contain at least one version");
        }
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index).versionNumber() != index + 1) {
                throw new IllegalArgumentException("Document versions must be contiguous from version 1");
            }
        }
        this.versions.addAll(ordered);
    }

    public static Document reconstitute(
            DocumentId id,
            DocumentType type,
            DocumentClassification classification,
            DocumentStatus status,
            DocumentMetadata metadata,
            List<DocumentVersion> versions
    ) {
        return new Document(id, type, classification, status, metadata, versions);
    }

    public DocumentVersion addVersion(String newHash, String newStorageKey, DocumentMetadata updatedMetadata) {
        return addVersion(newHash, newStorageKey, updatedMetadata, Instant.now());
    }

    public DocumentVersion addVersion(
            String newHash,
            String newStorageKey,
            DocumentMetadata updatedMetadata,
            Instant occurredAt
    ) {
        if (status != DocumentStatus.ACTIVE)
            throw new IllegalStateException("Cannot add version to non-active document: " + status);
        int nextNum = versions.size() + 1;
        DocumentVersion v = new DocumentVersion(
                DocumentVersionId.generate(), nextNum, newHash, newStorageKey, Objects.requireNonNull(occurredAt));
        versions.add(v);
        if (updatedMetadata != null) {
            this.metadata = updatedMetadata;
        }
        raise(new DocumentVersionCreated(id, occurredAt, v.id(), v.versionNumber()));
        return v;
    }

    public void publish() {
        publish(Instant.now());
    }

    public void publish(Instant occurredAt) {
        if (status == DocumentStatus.DELETED) {
            throw new IllegalStateException("Cannot publish deleted document");
        }
        if (status == DocumentStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot publish archived document");
        }
        if (status == DocumentStatus.PUBLISHED) {
            return;
        }
        status = DocumentStatus.PUBLISHED;
        raise(new DocumentPublished(id, Objects.requireNonNull(occurredAt), latestVersion().id()));
    }

    public void archive() {
        archive(Instant.now());
    }

    public void archive(Instant occurredAt) {
        if (status == DocumentStatus.DELETED) throw new IllegalStateException("Cannot archive deleted document");
        if (status == DocumentStatus.ARCHIVED) return;
        this.status = DocumentStatus.ARCHIVED;
        raise(new DocumentArchived(id, Objects.requireNonNull(occurredAt)));
    }

    public void markDeleted() {
        markDeleted(Instant.now());
    }

    public void markDeleted(Instant occurredAt) {
        if (status == DocumentStatus.DELETED) return;
        this.status = DocumentStatus.DELETED;
        raise(new DocumentDeleted(id, Objects.requireNonNull(occurredAt)));
    }

    public DocumentType type() { return type; }
    public DocumentClassification classification() { return classification; }
    public DocumentStatus status() { return status; }
    public DocumentMetadata metadata() { return metadata; }
    public List<DocumentVersion> versions() { return List.copyOf(versions); }
    public DocumentVersion latestVersion() { return versions.get(versions.size() - 1); }
}
