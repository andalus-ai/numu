package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.valueobject.AssetStatus;
import tech.kayys.numou.asset.domain.valueobject.AssetType;
import tech.kayys.numou.foundation.persistence.BaseEntity;

@Entity
@Table(name = "asset")
public class AssetEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "asset_number", nullable = false, length = 100)
    public String assetNumber;

    @Column(name = "name", nullable = false, length = 255)
    public String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 50)
    public AssetType assetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    public AssetStatus status;

    @Column(name = "location_id", length = 100)
    public String locationId;

    @Column(name = "location_name", length = 255)
    public String locationName;

    @Column(name = "party_id", length = 100)
    public String partyId;

    @Column(name = "party_type", length = 50)
    public String partyType;

    @Column(name = "party_name", length = 255)
    public String partyName;

    @Column(name = "classification_id", length = 100)
    public String classificationId;

    @Column(name = "classification_name", length = 255)
    public String classificationName;
}
