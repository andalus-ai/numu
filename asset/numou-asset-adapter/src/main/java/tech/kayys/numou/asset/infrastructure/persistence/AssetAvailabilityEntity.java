package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityReason;
import tech.kayys.numou.asset.domain.availability.AssetAvailabilityType;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;
import java.util.UUID;

/** Persistence row for an availability period (ASSET-26 §8). */
@Entity
@Table(name = "asset_availability_period")
public class AssetAvailabilityEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "asset_id", nullable = false)
    public UUID assetId;

    @Column(name = "starts_at", nullable = false)
    public Instant startsAt;

    /** Nullable: an open-ended (indefinite) state. */
    @Column(name = "ends_at")
    public Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_type", nullable = false, length = 50)
    public AssetAvailabilityType availabilityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 50)
    public AssetAvailabilityReason reason;

    @Column(name = "reference_id", length = 200)
    public String referenceId;

    @Column(name = "notes", length = 1000)
    public String notes;
}