package tech.kayys.syirkah.document.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_document_version_number",
                columnNames = {"tenant_id", "document_id", "version_number"}),
        @UniqueConstraint(name = "uq_document_version_id",
                columnNames = {"tenant_id", "document_id", "id"})
})
public class DocumentVersionEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "document_id", nullable = false)
    public UUID documentId;

    @Column(name = "version_number", nullable = false)
    public int versionNumber;

    @Column(name = "sha256_hash", nullable = false)
    public String sha256Hash;

    @Column(name = "storage_key", nullable = false)
    public String storageKey;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Version
    @Column(nullable = false)
    public long version;
}
