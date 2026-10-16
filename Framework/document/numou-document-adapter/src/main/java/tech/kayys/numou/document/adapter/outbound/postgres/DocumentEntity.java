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
@Table(name = "documents", uniqueConstraints =
        @UniqueConstraint(name = "uq_document_tenant_id", columnNames = {"tenant_id", "id"}))
public class DocumentEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "document_type", nullable = false)
    public String documentType;

    @Column(nullable = false)
    public String classification;

    @Column(nullable = false)
    public String status;

    @Column(nullable = false)
    public String filename;

    @Column(name = "content_type", nullable = false)
    public String contentType;

    @Column(name = "file_size", nullable = false)
    public long fileSize;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_attributes", nullable = false, columnDefinition = "jsonb")
    public Map<String, String> customAttributes;

    @Version
    @Column(nullable = false)
    public long version;
}
