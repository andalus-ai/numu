package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.domain.movement.AssetMovementType;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only movement history row (ASSET-13).
 *
 * <p>Rows are never updated: a correction is a new movement.
 * {@code sourceEventId} makes the projection idempotent.</p>
 */
@Entity
@Table(name = "asset_movement")
public class AssetMovementEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "asset_id", nullable = false)
    public UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 40)
    public AssetMovementType movementType;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "from_location_id", length = 100)
    public String fromLocationId;

    @Column(name = "from_party_id", length = 100)
    public String fromPartyId;

    @Column(name = "to_location_id", length = 100)
    public String toLocationId;

    @Column(name = "to_party_id", length = 100)
    public String toPartyId;

    @Column(name = "classification_id", length = 100)
    public String classificationId;

    @Column(name = "related_asset_id")
    public UUID relatedAssetId;

    @Column(name = "source_event_id", nullable = false, unique = true, length = 40)
    public UUID sourceEventId;
}
