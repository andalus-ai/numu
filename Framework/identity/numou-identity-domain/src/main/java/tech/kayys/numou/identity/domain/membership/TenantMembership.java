package tech.kayys.syirkah.identity.domain.membership;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TenantMembership extends AbstractAggregateRoot<MembershipId> {
    private final TenantId tenantId;
    private final UserId userId;
    private final Instant invitedAt;
    private final Instant invitationExpiresAt;
    private MembershipStatus status;
    private Instant activatedAt;
    private Instant revokedAt;

    private TenantMembership(
            MembershipId id,
            TenantId tenantId,
            UserId userId,
            Instant invitedAt,
            Instant invitationExpiresAt,
            MembershipStatus status
    ) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.invitedAt = Objects.requireNonNull(invitedAt, "invitedAt cannot be null");
        this.invitationExpiresAt = Objects.requireNonNull(invitationExpiresAt, "invitationExpiresAt cannot be null");
        if (!invitationExpiresAt.isAfter(invitedAt)) {
            throw new IllegalArgumentException("invitation expiry must be after invitation time");
        }
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public static TenantMembership invite(
            MembershipId id,
            TenantId tenantId,
            UserId userId,
            Instant invitedAt,
            Instant invitationExpiresAt
    ) {
        var membership = new TenantMembership(
                id, tenantId, userId, invitedAt, invitationExpiresAt, MembershipStatus.INVITED
        );
        membership.raise(new MembershipStatusChanged(
                UUID.randomUUID(), invitedAt, id, tenantId, userId, MembershipStatus.INVITED, null
        ));
        return membership;
    }

    public static TenantMembership reconstitute(
            MembershipId id,
            TenantId tenantId,
            UserId userId,
            Instant invitedAt,
            Instant invitationExpiresAt,
            MembershipStatus status,
            Instant activatedAt,
            Instant revokedAt
    ) {
        var membership = new TenantMembership(id, tenantId, userId, invitedAt, invitationExpiresAt, status);
        membership.activatedAt = activatedAt;
        membership.revokedAt = revokedAt;
        return membership;
    }

    public void acceptInvitation(Instant now) {
        requireStatus(MembershipStatus.INVITED);
        Objects.requireNonNull(now, "now cannot be null");
        if (!now.isBefore(invitationExpiresAt)) {
            throw new IllegalStateException("Membership invitation has expired");
        }
        status = MembershipStatus.ACTIVE;
        activatedAt = now;
        raise(new MembershipStatusChanged(UUID.randomUUID(), now, id(), tenantId, userId, status, null));
    }

    public void suspend(String reason, Instant now) {
        requireStatus(MembershipStatus.ACTIVE);
        status = MembershipStatus.SUSPENDED;
        raise(new MembershipStatusChanged(
                UUID.randomUUID(), Objects.requireNonNull(now), id(), tenantId, userId, status, requireReason(reason)
        ));
    }

    public void reactivate(Instant now) {
        requireStatus(MembershipStatus.SUSPENDED);
        status = MembershipStatus.ACTIVE;
        raise(new MembershipStatusChanged(
                UUID.randomUUID(), Objects.requireNonNull(now), id(), tenantId, userId, status, null
        ));
    }

    public void revoke(String reason, Instant now) {
        if (status == MembershipStatus.REVOKED) {
            throw new IllegalStateException("Membership has already been revoked");
        }
        status = MembershipStatus.REVOKED;
        revokedAt = Objects.requireNonNull(now, "now cannot be null");
        raise(new MembershipStatusChanged(
                UUID.randomUUID(), now, id(), tenantId, userId, status, requireReason(reason)
        ));
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    private void requireStatus(MembershipStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected membership status " + expected + " but was " + status);
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason cannot be blank");
        }
        return reason;
    }

    public TenantId tenantId() { return tenantId; }
    public UserId userId() { return userId; }
    public Instant invitedAt() { return invitedAt; }
    public Instant invitationExpiresAt() { return invitationExpiresAt; }
    public MembershipStatus status() { return status; }
    public Instant activatedAt() { return activatedAt; }
    public Instant revokedAt() { return revokedAt; }
}
