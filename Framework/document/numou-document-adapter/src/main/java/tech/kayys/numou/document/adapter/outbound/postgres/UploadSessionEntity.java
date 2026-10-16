package tech.kayys.syirkah.document.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "document_upload_sessions", uniqueConstraints =
        @UniqueConstraint(name = "uq_upload_session_tenant_id", columnNames = {"tenant_id", "id"}))
public class UploadSessionEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "storage_key", nullable = false)
    public String storageKey;

    @Column(name = "document_type", nullable = false)
    public String documentType;

    @Column(nullable = false)
    public String classification;

    @Column(nullable = false)
    public String filename;

    @Column(name = "content_type", nullable = false)
    public String contentType;

    @Column(name = "expected_size", nullable = false)
    public long expectedSize;

    @Column(name = "expected_sha256")
    public String expectedSha256;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_attributes", nullable = false, columnDefinition = "jsonb")
    public Map<String, String> customAttributes;

    @Column(name = "expires_at", nullable = false)
    public java.time.Instant expiresAt;

    @Column(nullable = false)
    public String status;

    @Column(name = "completed_document_id")
    public UUID completedDocumentId;

    @Column(name = "completed_version_id")
    public UUID completedVersionId;

    @Version
    @Column(nullable = false)
    public long version;
}
