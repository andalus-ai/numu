package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.availability.AssetUtilizationType;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Persistence row for a utilization observation (ASSET-26 §15). */
@Entity
@Table(name = "asset_utilization")
public class AssetUtilizationEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "asset_id", nullable = false)
    public UUID assetId;

    @Column(name = "starts_at", nullable = false)
    public Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    public Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "utilization_type", nullable = false, length = 50)
    public AssetUtilizationType utilizationType;

    @Column(name = "quantity", nullable = false, precision = 24, scale = 8)
    public BigDecimal quantity;

    @Column(name = "unit", length = 50)
    public String unit;

    @Column(name = "source", length = 100)
    public String source;

    @Column(name = "reference_id", length = 200)
    public String referenceId;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;
}