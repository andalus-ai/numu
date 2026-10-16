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
@Table(name = "tenant_memberships", uniqueConstraints =
        @UniqueConstraint(name = "uq_membership_tenant_user", columnNames = {"tenant_id", "user_id"}))
public class TenantMembershipEntity extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(name = "tenant_id", nullable = false)
    public UUID tenantId;

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(nullable = false)
    public String status;

    @Column(name = "invited_at", nullable = false)
    public Instant invitedAt;

    @Column(name = "invitation_expires_at", nullable = false)
    public Instant invitationExpiresAt;

    @Column(name = "activated_at")
    public Instant activatedAt;

    @Column(name = "revoked_at")
    public Instant revokedAt;

    @Version
    @Column(nullable = false)
    public long version;
}
