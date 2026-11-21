package tech.kayys.numou.organization.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationInvitationTest {
    @Test
    void rotatesSecretAndInvalidatesPreviousDigest() {
        Instant now = Instant.parse("2026-11-05T00:00:00Z");
        var invitation = invitation(now);
        String oldDigest = invitation.tokenHash();

        invitation.rotate("b".repeat(64), now.plus(14, ChronoUnit.DAYS), now.plusSeconds(1));

        assertNotEquals(oldDigest, invitation.tokenHash());
        assertEquals(1, invitation.getVersion());
        assertEquals(OrganizationInvitationStatus.PENDING, invitation.status());
    }

    @Test
    void acceptsOnlyForTheVerifiedRecipientEmail() {
        Instant now = Instant.parse("2026-11-05T00:00:00Z");
        var invitation = invitation(now);
        UserId acceptedBy = UserId.newId();

        assertThrows(IllegalStateException.class,
                () -> invitation.accept(acceptedBy, "attacker@example.com", now.plusSeconds(1)));

        invitation.accept(acceptedBy, " DEVELOPER@example.com ", now.plusSeconds(2));
        assertEquals(OrganizationInvitationStatus.ACCEPTED, invitation.status());
        assertEquals(acceptedBy, invitation.acceptedBy());
        assertNotNull(invitation.acceptedAt());
    }

    @Test
    void rejectsExpiredAndRevokedInvitations() {
        Instant now = Instant.parse("2026-11-05T00:00:00Z");
        var expired = invitation(now);
        expired.expire(now.plus(8, ChronoUnit.DAYS));
        assertEquals(OrganizationInvitationStatus.EXPIRED, expired.status());
        assertThrows(IllegalStateException.class,
                () -> expired.rotate("c".repeat(64), now.plus(10, ChronoUnit.DAYS), now.plus(8, ChronoUnit.DAYS)));

        var revoked = invitation(now);
        revoked.revoke(now.plusSeconds(1));
        assertEquals(OrganizationInvitationStatus.REVOKED, revoked.status());
        assertThrows(IllegalStateException.class,
                () -> revoked.accept(UserId.newId(), "developer@example.com", now.plusSeconds(2)));
    }

    @Test
    void refusesOwnerRoleAndMalformedDigest() {
        Instant now = Instant.parse("2026-11-05T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> OrganizationInvitation.issue(
                OrganizationInvitationId.generate(), TenantId.generate(), OrganizationId.generate(),
                "developer@example.com", OrganizationMembership.Role.OWNER, UserId.newId(),
                "a".repeat(64), now.plusSeconds(10), now));
        assertThrows(IllegalArgumentException.class, () -> OrganizationInvitation.issue(
                OrganizationInvitationId.generate(), TenantId.generate(), OrganizationId.generate(),
                "developer@example.com", OrganizationMembership.Role.DEVELOPER, UserId.newId(),
                "not-a-digest", now.plusSeconds(10), now));
    }

    private static OrganizationInvitation invitation(Instant now) {
        return OrganizationInvitation.issue(OrganizationInvitationId.generate(), TenantId.generate(),
                OrganizationId.generate(), " DEVELOPER@EXAMPLE.COM ", OrganizationMembership.Role.DEVELOPER,
                UserId.newId(), "a".repeat(64), now.plus(7, ChronoUnit.DAYS), now);
    }
}