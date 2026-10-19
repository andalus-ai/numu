package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.finance.FixedAssetStatus;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Accounting-owned financial snapshot projection (ASSET-25 §26). */
@Entity
@Table(name = "asset_financial_snapshot")
public class AssetFinancialSnapshotEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "asset_id", nullable = false)
    public UUID assetId;

    @Column(name = "accounting_asset_id")
    public UUID accountingAssetId;

    @Column(name = "acquisition_cost", precision = 19, scale = 4)
    public BigDecimal acquisitionCost;

    @Column(name = "accumulated_depreciation", precision = 19, scale = 4)
    public BigDecimal accumulatedDepreciation;

    @Column(name = "net_book_value", precision = 19, scale = 4)
    public BigDecimal netBookValue;

    @Column(name = "impairment_amount", precision = 19, scale = 4)
    public BigDecimal impairmentAmount;

    @Column(name = "proceeds", precision = 19, scale = 4)
    public BigDecimal proceeds;

    @Column(name = "currency", length = 3)
    public String currency;

    @Column(name = "capitalization_date")
    public Instant capitalizationDate;

    @Column(name = "last_depreciation_date")
    public Instant lastDepreciationDate;

    @Column(name = "disposed_at")
    public Instant disposedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "fixed_asset_status", length = 50)
    public FixedAssetStatus fixedAssetStatus;

    @Column(name = "as_of")
    public Instant asOf;

    @Column(name = "source_event_key", length = 200)
    public String sourceEventKey;

    @Column(name = "source_kind", length = 50)
    public String sourceKind;
}