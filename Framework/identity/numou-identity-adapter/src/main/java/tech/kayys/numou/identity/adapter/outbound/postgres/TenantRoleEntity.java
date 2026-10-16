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
@Table(name = "tenant_roles", uniqueConstraints = {
        @UniqueConstraint(name = "uq_tenant_role_id", columnNames = {"tenant_id", "id"}),
        @UniqueConstraint(name = "uq_tenant_role_code", columnNames = {"tenant_id", "code"})
})
public class TenantRoleEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(nullable = false)
    public String code;

    @Column(nullable = false)
    public String name;

    @Column(name = "built_in", nullable = false)
    public boolean builtIn;

    @Version
    @Column(nullable = false)
    public long version;
}
