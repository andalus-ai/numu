package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;
import java.util.UUID;

/** The asset→accounting link row (ASSET-25 §29). Not financial state itself. */
@Entity
@Table(name = "asset_accounting_link")
public class AssetAccountingLinkEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "asset_id", nullable = false)
    public UUID assetId;

    @Column(name = "accounting_asset_id", nullable = false)
    public UUID accountingAssetId;

    @Column(name = "accounting_asset_number", length = 100)
    public String accountingAssetNumber;

    @Column(name = "linked_at", nullable = false)
    public Instant linkedAt;

    @Column(name = "linked_by", length = 100)
    public String linkedBy;

    @Column(name = "link_active", nullable = false)
    public boolean linkActive = true;
}