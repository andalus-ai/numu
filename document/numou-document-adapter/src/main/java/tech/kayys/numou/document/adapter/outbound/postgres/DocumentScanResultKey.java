package tech.kayys.numou.document.adapter.outbound.postgres;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key matching the tenant/document/version primary key. */
public final class DocumentScanResultKey implements Serializable {
    public UUID tenantId;
    public UUID documentId;
    public UUID documentVersionId;

    public DocumentScanResultKey() {
    }

    public DocumentScanResultKey(UUID tenantId, UUID documentId, UUID documentVersionId) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.documentId = Objects.requireNonNull(documentId);
        this.documentVersionId = Objects.requireNonNull(documentVersionId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof DocumentScanResultKey that)) return false;
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(documentId, that.documentId)
                && Objects.equals(documentVersionId, that.documentVersionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, documentId, documentVersionId);
    }
}
