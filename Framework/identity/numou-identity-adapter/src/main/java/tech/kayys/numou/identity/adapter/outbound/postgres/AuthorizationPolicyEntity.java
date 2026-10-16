package tech.kayys.syirkah.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "authorization_policies")
public class AuthorizationPolicyEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "permission_code", nullable = false)
    public String permissionCode;

    @Column(nullable = false)
    public String effect;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition_json", nullable = false, columnDefinition = "jsonb")
    public String conditionJson;

    @Column(nullable = false)
    public boolean active;

    @Version
    @Column(nullable = false)
    public long version;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
