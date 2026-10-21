package tech.kayys.numou.identity.adapter.outbound.postgres;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "personal_access_token")
public class PersonalAccessTokenEntity extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(name = "principal_type", nullable = false)
    public String principalType;

    @Column(name = "principal_id", nullable = false)
    public UUID principalId;

    @Column(nullable = false)
    public String label;

    @Column(name = "token_hash", nullable = false, unique = true)
    public String tokenHash;

    @Column(name = "display_prefix", nullable = false)
    public String displayPrefix;

    @Column(nullable = false)
    public String status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "expires_at")
    public Instant expiresAt;

    @Column(name = "last_used_at")
    public Instant lastUsedAt;

    @Column(name = "revoked_at")
    public Instant revokedAt;

    // --- AuditMeta (tech.kayys.numou.foundation.domain.audit) ---
    @Column(name = "audit_created_by")
    public String auditCreatedBy;

    @Column(name = "audit_updated_by")
    public String auditUpdatedBy;

    @Column(name = "audit_updated_at")
    public Instant auditUpdatedAt;
}
