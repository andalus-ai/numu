package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.document.AssetDocumentType;
import tech.kayys.numou.foundation.persistence.BaseEntity;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_document_reference")
public class AssetDocumentReferenceEntity extends BaseEntity {
    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;
    @Column(name = "asset_id", nullable = false)
    public UUID assetId;
    @Column(name = "document_id", nullable = false)
    public UUID documentId;
    @Column(name = "document_version")
    public Integer documentVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 100)
    public AssetDocumentType documentType;
    @Column(name = "title", nullable = false, length = 255)
    public String title;
    @Column(name = "primary_document", nullable = false)
    public boolean primaryDocument;
    @Column(name = "required", nullable = false)
    public boolean required;
    @Column(name = "valid_from")
    public Instant validFrom;
    @Column(name = "valid_until")
    public Instant validUntil;
    @Column(name = "linked_at", nullable = false)
    public Instant linkedAt;
    @Column(name = "linked_by", length = 100)
    public String linkedBy;
}
