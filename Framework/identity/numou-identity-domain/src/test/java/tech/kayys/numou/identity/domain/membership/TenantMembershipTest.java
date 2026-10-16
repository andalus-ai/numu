package tech.kayys.syirkah.identity.domain.membership;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.domain.user.UserId;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantMembershipTest {
    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void membership_requires_invitation_acceptance_before_activation() {
        var membership = membership(NOW.plusSeconds(300));

        assertFalse(membership.isActive());
        membership.acceptInvitation(NOW.plusSeconds(1));
        assertTrue(membership.isActive());
        assertEquals(MembershipStatus.ACTIVE, membership.status());
        assertEquals(2, membership.pullDomainEvents().size());
    }

    @Test
    void revoked_membership_is_terminal_and_expired_invitation_cannot_be_accepted() {
        var membership = membership(NOW.plusSeconds(300));
        membership.acceptInvitation(NOW.plusSeconds(1));
        membership.revoke("offboarded", NOW.plusSeconds(2));

        assertEquals(MembershipStatus.REVOKED, membership.status());
        assertThrows(IllegalStateException.class, () -> membership.reactivate(NOW.plusSeconds(3)));
        assertThrows(IllegalStateException.class, () -> membership.acceptInvitation(NOW.plusSeconds(3)));

        var expired = membership(NOW.plusSeconds(2));
        assertThrows(IllegalStateException.class, () -> expired.acceptInvitation(NOW.plusSeconds(2)));
    }

    private static TenantMembership membership(Instant expiresAt) {
        return TenantMembership.invite(
                MembershipId.generate(),
                TenantId.generate(),
                UserId.newId(),
                NOW,
                expiresAt
        );
    }
}
