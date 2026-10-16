package tech.kayys.syirkah.identity.adapter.outbound.postgres;

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
@Table(name = "tenant_user_roles", uniqueConstraints =
        @UniqueConstraint(name = "uq_tenant_user_role", columnNames = {"tenant_id", "user_id", "role_id"}))
public class TenantUserRoleEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "role_id", nullable = false)
    public UUID roleId;

    @Column(name = "assigned_at", nullable = false)
    public Instant assignedAt;

    @Column(name = "assigned_by")
    public UUID assignedBy;

    @Version
    @Column(nullable = false)
    public long version;
}
