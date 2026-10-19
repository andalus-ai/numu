package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.relationship.AssetRelationshipType;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.util.UUID;

@Entity
@Table(name = "asset_relationship")
public class AssetRelationshipEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "source_asset_id", nullable = false)
    public UUID sourceAssetId;

    @Column(name = "related_asset_id", nullable = false)
    public UUID relatedAssetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 50)
    public AssetRelationshipType relationshipType;
}
