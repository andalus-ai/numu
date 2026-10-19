package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.classification.AssetAttributeType;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.util.UUID;

/**
 * A single dynamic attribute attached to an asset (ASSET-15).
 *
 * <p>Kept outside the Asset aggregate so the aggregate never becomes a
 * schema-less {@code Map} bag.</p>
 */
@Entity
@Table(
        name = "asset_attribute",
        indexes = @Index(name = "ix_asset_attribute_asset", columnList = "tenant_id, asset_id")
)
public class AssetAttributeEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "asset_id", nullable = false)
    public UUID assetId;

    @Column(name = "attribute_key", nullable = false, length = 100)
    public String attributeKey;

    @Column(name = "attribute_value", nullable = false, length = 1000)
    public String attributeValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_type", nullable = false, length = 20)
    public AssetAttributeType attributeType;
}
