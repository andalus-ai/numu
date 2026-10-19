package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.finance.AssetAccountingReconciliation.ReconciliationStatus;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;
import java.util.UUID;

/** Reconciliation entry between the operational asset and its accounting link (ASSET-25 §34). */
@Entity
@Table(name = "asset_accounting_reconciliation")
public class AssetAccountingReconciliationEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "asset_id", nullable = false)
    public UUID assetId;

    @Column(name = "accounting_asset_id")
    public UUID accountingAssetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 50)
    public ReconciliationStatus reconciliationStatus;

    @Column(name = "detail", length = 1000)
    public String detail;

    @Column(name = "reconciled_at", nullable = false)
    public Instant reconciledAt;

    @Column(name = "reconciled_by", length = 100)
    public String reconciledBy;
}