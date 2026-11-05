package tech.kayys.numou.organization.domain;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public final class OrganizationInvitation extends AbstractAggregateRoot<OrganizationInvitationId> {
    private final TenantId tenantId;
    private final OrganizationId organizationId;
    private final String recipientEmail;
    private final OrganizationMembership.Role role;
    private final UserId invitedBy;
    private String tokenHash;
    private Instant expiresAt;
    private OrganizationInvitationStatus status;
    private UserId acceptedBy;
    private Instant acceptedAt;
    private Instant revokedAt;

    private OrganizationInvitation(OrganizationInvitationId id, TenantId tenantId,
            OrganizationId organizationId, String recipientEmail, OrganizationMembership.Role role,
            UserId invitedBy, String tokenHash, Instant expiresAt, OrganizationInvitationStatus status,
            UserId acceptedBy, Instant acceptedAt, Instant revokedAt) {
        super(Objects.requireNonNull(id));
        this.tenantId = Objects.requireNonNull(tenantId);
        this.organizationId = Objects.requireNonNull(organizationId);
        this.recipientEmail = normalize(recipientEmail);
        this.role = Objects.requireNonNull(role);
        if (role == OrganizationMembership.Role.OWNER) throw new IllegalArgumentException("OWNER cannot be invited");
        this.invitedBy = Objects.requireNonNull(invitedBy);
        this.tokenHash = requireHash(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.status = Objects.requireNonNull(status);
        this.acceptedBy = acceptedBy;
        this.acceptedAt = acceptedAt;
        this.revokedAt = revokedAt;
        validateState();
    }

    public static OrganizationInvitation issue(OrganizationInvitationId id, TenantId tenantId,
            OrganizationId organizationId, String email, OrganizationMembership.Role role,
            UserId invitedBy, String hash, Instant expiry, Instant now) {
        Objects.requireNonNull(now);
        if (!expiry.isAfter(now)) throw new IllegalArgumentException("Expiry must be in the future");
        var value = new OrganizationInvitation(id, tenantId, organizationId, email, role,
                invitedBy, hash, expiry, OrganizationInvitationStatus.PENDING, null, null, null);
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        return value;
    }

    public static OrganizationInvitation restore(OrganizationInvitationId id, TenantId tenantId,
            OrganizationId organizationId, String email, OrganizationMembership.Role role,
            UserId invitedBy, String hash, Instant expiry, OrganizationInvitationStatus status,
            UserId acceptedBy, Instant acceptedAt, Instant revokedAt, Instant createdAt,
            Instant updatedAt, int version) {
        var value = new OrganizationInvitation(id, tenantId, organizationId, email, role,
                invitedBy, hash, expiry, status, acceptedBy, acceptedAt, revokedAt);
        value.setCreatedAt(createdAt);
        value.setUpdatedAt(updatedAt);
        value.setVersion(version);
        return value;
    }

    public void rotate(String hash, Instant expiry, Instant now) {
        requirePending(now);
        if (!expiry.isAfter(now)) throw new IllegalArgumentException("Expiry must be in the future");
        tokenHash = requireHash(hash);
        expiresAt = expiry;
        touch(now);
    }

    public void accept(UserId userId, String verifiedEmail, Instant now) {
        requirePending(now);
        if (!recipientEmail.equals(normalize(verifiedEmail))) {
            throw new IllegalStateException("Verified email does not match invitation recipient");
        }
        acceptedBy = Objects.requireNonNull(userId);
        acceptedAt = Objects.requireNonNull(now);
        status = OrganizationInvitationStatus.ACCEPTED;
        touch(now);
    }

    public void revoke(Instant now) {
        if (status == OrganizationInvitationStatus.REVOKED) return;
        requirePending(now);
        status = OrganizationInvitationStatus.REVOKED;
        revokedAt = now;
        touch(now);
    }

    public void expire(Instant now) {
        if (status == OrganizationInvitationStatus.PENDING && !now.isBefore(expiresAt)) {
            status = OrganizationInvitationStatus.EXPIRED;
            touch(now);
        }
    }

    private void requirePending(Instant now) {
        Objects.requireNonNull(now);
        if (status != OrganizationInvitationStatus.PENDING) throw new IllegalStateException("Invitation is not pending");
        if (!now.isBefore(expiresAt)) throw new IllegalStateException("Invitation has expired");
    }

    private void touch(Instant now) {
        setUpdatedAt(now);
        incrementVersion();
    }

    private void validateState() {
        if (status == OrganizationInvitationStatus.ACCEPTED && (acceptedBy == null || acceptedAt == null))
            throw new IllegalArgumentException("Accepted invitation requires acceptance details");
        if (status != OrganizationInvitationStatus.ACCEPTED && (acceptedBy != null || acceptedAt != null))
            throw new IllegalArgumentException("Unexpected acceptance details");
        if (status == OrganizationInvitationStatus.REVOKED && revokedAt == null)
            throw new IllegalArgumentException("Revoked invitation requires revokedAt");
        if (status != OrganizationInvitationStatus.REVOKED && revokedAt != null)
            throw new IllegalArgumentException("Unexpected revokedAt");
    }

    private static String normalize(String email) {
        Objects.requireNonNull(email);
        String value = email.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 320 || !value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IllegalArgumentException("Invalid email");
        return value;
    }

    private static String requireHash(String hash) {
        Objects.requireNonNull(hash);
        if (!hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid digest");
        return hash;
    }

    public TenantId tenantId() { return tenantId; }
    public OrganizationId organizationId() { return organizationId; }
    public String recipientEmail() { return recipientEmail; }
    public OrganizationMembership.Role role() { return role; }
    public UserId invitedBy() { return invitedBy; }
    public String tokenHash() { return tokenHash; }
    public Instant expiresAt() { return expiresAt; }
    public OrganizationInvitationStatus status() { return status; }
    public UserId acceptedBy() { return acceptedBy; }
    public Instant acceptedAt() { return acceptedAt; }
    public Instant revokedAt() { return revokedAt; }
}