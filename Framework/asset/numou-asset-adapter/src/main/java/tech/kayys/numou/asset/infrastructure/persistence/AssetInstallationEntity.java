package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.installation.AssetInstallationStatus;
import tech.kayys.numou.asset.domain.relationship.AssetRelationshipType;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "asset_installation")
public class AssetInstallationEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "component_asset_id", nullable = false)
    public UUID componentAssetId;

    @Column(name = "parent_asset_id", nullable = false)
    public UUID parentAssetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 50)
    public AssetRelationshipType relationshipType;

    @Column(name = "installed_at", nullable = false)
    public Instant installedAt;

    @Column(name = "installed_by", length = 100)
    public String installedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    public AssetInstallationStatus status;

    @Column(name = "removed_at")
    public Instant removedAt;

    @Column(name = "removed_by", length = 100)
    public String removedBy;
}
