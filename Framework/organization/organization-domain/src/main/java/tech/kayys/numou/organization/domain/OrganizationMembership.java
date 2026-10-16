package tech.kayys.syirkah.organization.domain;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.time.Instant;
import java.util.Objects;

public final class OrganizationMembership extends AbstractAggregateRoot<OrganizationMembershipId> {

    private final OrganizationId organizationId;
    private final UserId userId;
    private OrganizationMembershipStatus status;

    private OrganizationMembership(OrganizationMembershipId id, OrganizationId organizationId,
                                   UserId userId, OrganizationMembershipStatus status) {
        super(Objects.requireNonNull(id, "id cannot be null"));
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    private OrganizationMembership() {
        super();
        this.organizationId = null;
        this.userId = null;
    }

    public static OrganizationMembership invite(OrganizationMembershipId id,
                                                OrganizationId organizationId, UserId userId) {
        return new OrganizationMembership(id, organizationId, userId, OrganizationMembershipStatus.PENDING);
    }

    public static OrganizationMembership restore(OrganizationMembershipId id,
                                                  OrganizationId organizationId, UserId userId,
                                                  OrganizationMembershipStatus status) {
        return new OrganizationMembership(id, organizationId, userId, status);
    }

    public void activate() {
        transition(OrganizationMembershipStatus.ACTIVE,
                OrganizationMembershipStatus.PENDING, OrganizationMembershipStatus.SUSPENDED);
    }

    public void suspend() {
        transition(OrganizationMembershipStatus.SUSPENDED,
                OrganizationMembershipStatus.ACTIVE, OrganizationMembershipStatus.PENDING);
    }

    public void revoke() {
        if (status == OrganizationMembershipStatus.REVOKED) {
            return;
        }
        status = OrganizationMembershipStatus.REVOKED;
        touch();
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
    public boolean isActive() { return status == OrganizationMembershipStatus.ACTIVE; }
}
