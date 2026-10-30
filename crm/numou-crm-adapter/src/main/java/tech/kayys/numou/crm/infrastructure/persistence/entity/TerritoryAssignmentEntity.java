package tech.kayys.numou.crm.infrastructure.persistence.entity;

import tech.kayys.numou.crm.domain.territory.TerritoryAssignmentOrigin;
import tech.kayys.numou.crm.domain.territory.TerritoryAssignmentStatus;
import tech.kayys.numou.foundation.persistence.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * TerritoryAssignment entity for persistence.
 */
@Entity
@Table(name = "crm_territory_assignments", indexes = {
    @Index(name = "idx_assignment_territory", columnList = "territory_id"),
    @Index(name = "idx_assignment_account", columnList = "account_id"),
    @Index(name = "idx_assignment_status", columnList = "status"),
    @Index(name = "idx_assignment_tenant", columnList = "tenant_id"),
    @Index(name = "idx_assignment_tenant_territory", columnList = "tenant_id, territory_id"),
    @Index(name = "idx_assignment_tenant_account", columnList = "tenant_id, account_id")
})
public class TerritoryAssignmentEntity extends BaseEntity {

    @Column(name = "tenant_id")
    public UUID tenantId;

    @Column(name = "territory_id", nullable = false)
    public UUID territoryId;

    @Column(name = "account_id", nullable = false)
    public UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 30)
    public TerritoryAssignmentOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public TerritoryAssignmentStatus status;

    @Column(name = "assigned_by_user_id", nullable = false)
    public UUID assignedByUserId;

    @Column(name = "assigned_at", nullable = false)
    public Instant assignedAt;

    @Column(name = "ended_at")
    public Instant endedAt;
}
