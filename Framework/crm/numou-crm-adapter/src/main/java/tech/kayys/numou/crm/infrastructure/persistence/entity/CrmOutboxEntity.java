package tech.kayys.numou.crm.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import java.time.Instant;

/**
 * Transactional outbox row for CRM domain events.
 */
@Entity
@Table(name = "crm_outbox")
public class CrmOutboxEntity extends BaseEntity {

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
