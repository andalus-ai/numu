package tech.kayys.numou.asset.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;

/** Transactional outbox row (see ASSET-11). */
@Entity
@Table(name = "asset_outbox")
public class OutboxEventEntity extends BaseEntity {

    @Column(name = "tenant_id", length = 100)
    public String tenantId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    public String aggregateType;

    @Column(name = "event_type", nullable = false, length = 150)
    public String eventType;

    @Column(name = "payload", length = 4000)
    public String payload;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "processed", nullable = false)
    public boolean processed = false;
}
