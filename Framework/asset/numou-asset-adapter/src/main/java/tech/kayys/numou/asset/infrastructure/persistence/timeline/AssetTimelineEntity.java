package tech.kayys.numou.asset.infrastructure.persistence.timeline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import tech.kayys.numou.asset.application.timeline.AssetTimelineCategory;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;
import java.util.UUID;

/** Persistence row for an asset timeline entry (ASSET-27 §30). */
@Entity
@Table(name = "asset_timeline")
public class AssetTimelineEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 100)
    public String tenantId;

    @Column(name = "asset_id", nullable = false)
    public UUID assetId;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "event_type", nullable = false, length = 150)
    public String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    public AssetTimelineCategory category;

    @Column(name = "title", nullable = false, length = 255)
    public String title;

    @Column(name = "description", length = 2000)
    public String description;

    @Column(name = "source", length = 100)
    public String source;

    @Column(name = "source_reference", length = 200)
    public String sourceReference;

    @Column(name = "source_event_id", nullable = false)
    public UUID sourceEventId;
}