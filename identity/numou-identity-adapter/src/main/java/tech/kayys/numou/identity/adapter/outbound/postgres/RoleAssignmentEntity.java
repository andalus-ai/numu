package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_assignment")
public class RoleAssignmentEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "principal_type", nullable = false)
    public String principalType;

    @Column(name = "principal_id", nullable = false)
    public UUID principalId;

    @Column(name = "role_id", nullable = false)
    public UUID roleId;

    @Column(name = "scope_type", nullable = false)
    public String scopeType;

    @Column(name = "scope_path", nullable = false)
    public String scopePath;

    @Column(name = "granted_by_user_id")
    public UUID grantedByUserId;

    @Column(name = "granted_at", nullable = false)
    public Instant grantedAt;

    @Column(name = "expires_at")
    public Instant expiresAt;
}
