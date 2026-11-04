package tech.kayys.numou.organization.domain;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;

/**
 * A user's membership in an organization.
 *
 * <p>Membership lifecycle and organization-level role are owned here. Authentication,
 * invitation delivery, and fine-grained authorization remain owned by Identity,
 * Notification, and Security respectively.
 */
public final class OrganizationMembership extends AbstractAggregateRoot<OrganizationMembershipId> {

    public enum Role {
        OWNER,
        ADMIN,
        DEVELOPER,
        VIEWER
    }

    private final OrganizationId organizationId;
    private final UserId userId;
    private OrganizationMembershipStatus status;
    private Role role;

    private OrganizationMembership(OrganizationMembershipId id, OrganizationId organizationId,
                                   UserId userId, OrganizationMembershipStatus status, Role role) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
        this.role = Objects.requireNonNull(role, "role cannot be null");
    }

    private OrganizationMembership() {
        super();
        this.organizationId = null;
        this.userId = null;
        this.role = Role.VIEWER;
    }

    /** Backward-compatible invitation; new invitations default to the least-privileged role. */
    public static OrganizationMembership invite(OrganizationMembershipId id,
                                                OrganizationId organizationId, UserId userId) {
        return invite(id, organizationId, userId, Role.DEVELOPER);
    }

    public static OrganizationMembership invite(OrganizationMembershipId id,
                                                OrganizationId organizationId, UserId userId, Role role) {
        if (role == Role.OWNER) {
            throw new IllegalArgumentException("OWNER must be assigned through explicit ownership transfer");
        }
        return new OrganizationMembership(id, organizationId, userId,
                OrganizationMembershipStatus.PENDING, role);
    }

    /** Creates the initial owner membership during organization provisioning. */
    public static OrganizationMembership createOwner(OrganizationMembershipId id,
                                                       OrganizationId organizationId, UserId userId) {
        return new OrganizationMembership(id, organizationId, userId,
                OrganizationMembershipStatus.ACTIVE, Role.OWNER);
    }

    public static OrganizationMembership restore(OrganizationMembershipId id,
                                                  OrganizationId organizationId, UserId userId,
                                                  OrganizationMembershipStatus status) {
        return restore(id, organizationId, userId, status, Role.DEVELOPER);
    }

    public static OrganizationMembership restore(OrganizationMembershipId id,
                                                  OrganizationId organizationId, UserId userId,
                                                  OrganizationMembershipStatus status, Role role) {
        return new OrganizationMembership(id, organizationId, userId, status, role);
    }

    public void activate() {
        transition(OrganizationMembershipStatus.ACTIVE,
                OrganizationMembershipStatus.PENDING, OrganizationMembershipStatus.SUSPENDED);
    }

    public void suspend() {
        if (role == Role.OWNER) {
            throw new InvalidStateException("Organization ownership must be transferred before suspending its owner");
        }
        transition(OrganizationMembershipStatus.SUSPENDED,
                OrganizationMembershipStatus.ACTIVE, OrganizationMembershipStatus.PENDING);
    }

    public void revoke() {
        if (status == OrganizationMembershipStatus.REVOKED) return;
        if (role == Role.OWNER) {
            throw new InvalidStateException("Organization ownership must be transferred before revoking its owner");
        }
        status = OrganizationMembershipStatus.REVOKED;
        touch();
    }

    public void changeRole(Role newRole) {
        Objects.requireNonNull(newRole, "newRole cannot be null");
        if (status != OrganizationMembershipStatus.ACTIVE) {
            throw new InvalidStateException("Only active memberships can change role");
        }
        if (role == Role.OWNER || newRole == Role.OWNER) {
            throw new InvalidStateException("Owner changes require an explicit ownership-transfer workflow");
        }
        if (role != newRole) {
            role = newRole;
            touch();
        }
    }

    private void transition(OrganizationMembershipStatus target, OrganizationMembershipStatus... allowed) {
        for (OrganizationMembershipStatus current : allowed) {
            if (status == current) {
                status = target;
                touch();
                return;
            }
        }
        throw new InvalidStateException("Cannot change membership from " + status + " to " + target);
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    public OrganizationId organizationId() { return organizationId; }
    public UserId userId() { return userId; }
    public OrganizationMembershipStatus status() { return status; }
    public Role role() { return role; }
    public boolean isActive() { return status == OrganizationMembershipStatus.ACTIVE; }
}
