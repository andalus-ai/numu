package tech.kayys.syirkah.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.util.UUID;

@Entity
@Table(name = "tenant_role_permissions", uniqueConstraints =
        @UniqueConstraint(name = "uq_role_permission", columnNames = {"tenant_id", "role_id", "permission_id"}))
public class TenantRolePermissionEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "role_id", nullable = false)
    public UUID roleId;

    @Column(name = "permission_id", nullable = false)
    public UUID permissionId;

    @Version
    @Column(nullable = false)
    public long version;
}
