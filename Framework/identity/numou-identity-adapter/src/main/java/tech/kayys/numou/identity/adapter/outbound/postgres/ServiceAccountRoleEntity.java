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
@Table(name = "service_account_roles", uniqueConstraints =
        @UniqueConstraint(name = "uq_service_account_role",
                columnNames = {"tenant_id", "service_account_id", "role_id"}))
public class ServiceAccountRoleEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "service_account_id", nullable = false)
    public UUID serviceAccountId;

    @Column(name = "role_id", nullable = false)
    public UUID roleId;

    @Column(name = "assigned_at", nullable = false)
    public Instant assignedAt;

    @Version
    @Column(nullable = false)
    public long version;
}
