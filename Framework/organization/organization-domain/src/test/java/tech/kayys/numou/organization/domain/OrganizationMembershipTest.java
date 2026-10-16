package tech.kayys.syirkah.organization.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.identity.domain.user.UserId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrganizationMembershipTest {

    @Test
    void membershipUsesCanonicalIdentityAndHasExplicitLifecycle() {
        var userId = UserId.newId();
        var membership = OrganizationMembership.invite(
                OrganizationMembershipId.generate(), OrganizationId.generate(), userId);

        assertThat(membership.userId()).isEqualTo(userId);
        assertThat(membership.status()).isEqualTo(OrganizationMembershipStatus.PENDING);

        membership.activate();
        assertThat(membership.isActive()).isTrue();
        membership.suspend();
        assertThat(membership.status()).isEqualTo(OrganizationMembershipStatus.SUSPENDED);
        membership.activate();
        membership.revoke();
        assertThat(membership.status()).isEqualTo(OrganizationMembershipStatus.REVOKED);
        assertThatThrownBy(membership::activate)
                .isInstanceOf(InvalidStateException.class);
    }
}
