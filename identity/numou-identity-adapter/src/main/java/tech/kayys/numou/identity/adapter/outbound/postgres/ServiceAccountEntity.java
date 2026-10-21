package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_accounts", uniqueConstraints = {
        @UniqueConstraint(name = "uq_service_account_code", columnNames = {"tenant_id", "code"}),
        @UniqueConstraint(name = "uq_service_account_tenant_id", columnNames = {"tenant_id", "id"})
})
public class ServiceAccountEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "tenant_id")
    public UUID tenantId;

    @Column(nullable = false)
    public String code;

    @Column(nullable = false)
    public String name;

    @Column
    public String description;

    @Column(name = "owner_user_id")
    public UUID ownerUserId;

    @Column(nullable = false)
    public String status;

    @Column(name = "created_at")
    public Instant createdAt;

    @Column(name = "revoked_at")
    public Instant revokedAt;

    // --- AuditMeta (tech.kayys.numou.foundation.domain.audit) ---
    @Column(name = "audit_created_by")
    public String auditCreatedBy;

    @Column(name = "audit_updated_by")
    public String auditUpdatedBy;

    @Column(name = "audit_updated_at")
    public Instant auditUpdatedAt;

    @Version
    @Column(nullable = false)
    public long version;
}
