package tech.kayys.numou.organization.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.identity.domain.user.UserId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationMembershipTest {
    @Test
    void membershipUsesCanonicalIdentityAndHasExplicitLifecycle() {
        var userId = UserId.newId();
        var membership = OrganizationMembership.invite(OrganizationMembershipId.generate(), OrganizationId.generate(), userId);
        assertThat(membership.userId()).isEqualTo(userId);
        assertThat(membership.status()).isEqualTo(OrganizationMembershipStatus.PENDING);
        assertThat(membership.role()).isEqualTo(OrganizationMembership.Role.DEVELOPER);
        membership.activate();
        assertThat(membership.isActive()).isTrue();
        membership.suspend();
        assertThat(membership.status()).isEqualTo(OrganizationMembershipStatus.SUSPENDED);
        membership.activate();
        membership.revoke();
        assertThat(membership.status()).isEqualTo(OrganizationMembershipStatus.REVOKED);
        assertThatThrownBy(membership::activate).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void invitationCanAssignOnlyNonOwnerRoles() {
        var membership = OrganizationMembership.invite(OrganizationMembershipId.generate(), OrganizationId.generate(),
                UserId.newId(), OrganizationMembership.Role.VIEWER);
        assertThat(membership.role()).isEqualTo(OrganizationMembership.Role.VIEWER);
        assertThatThrownBy(() -> OrganizationMembership.invite(OrganizationMembershipId.generate(),
                OrganizationId.generate(), UserId.newId(), OrganizationMembership.Role.OWNER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activeMemberCanChangeRole() {
        var membership = OrganizationMembership.invite(OrganizationMembershipId.generate(), OrganizationId.generate(), UserId.newId());
        membership.activate();
        membership.changeRole(OrganizationMembership.Role.ADMIN);
        assertThat(membership.role()).isEqualTo(OrganizationMembership.Role.ADMIN);
    }

    @Test
    void ownerCannotBeDemotedSuspendedOrRevokedWithoutTransfer() {
        var owner = OrganizationMembership.createOwner(OrganizationMembershipId.generate(),
                OrganizationId.generate(), UserId.newId());
        assertThat(owner.role()).isEqualTo(OrganizationMembership.Role.OWNER);
        assertThatThrownBy(() -> owner.changeRole(OrganizationMembership.Role.ADMIN)).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(owner::suspend).isInstanceOf(InvalidStateException.class);
        assertThatThrownBy(owner::revoke).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void inactiveMembershipCannotChangeRole() {
        var membership = OrganizationMembership.invite(OrganizationMembershipId.generate(), OrganizationId.generate(), UserId.newId());
        assertThatThrownBy(() -> membership.changeRole(OrganizationMembership.Role.VIEWER))
                .isInstanceOf(InvalidStateException.class);
    }
}
