package tech.kayys.numou.ecosystem.domain.party;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyIdentityAssociationTest {

    @Test
    void createsTenantScopedActiveAssociationWithoutGrantingAuthorization() {
        var linkedAt = Instant.parse("2026-01-01T00:00:00Z");
        var association = PartyIdentityAssociation.link(UUID.randomUUID(),
                TenantId.generate(), PartyId.generate(),
                new IdentitySubjectRef("https://id.example", "subject-123"), linkedAt);

        assertTrue(association.isActive());
        assertNull(association.revokedAt());
        assertEquals("https://id.example", association.identity().issuer());
    }

    @Test
    void revokesAssociationImmutably() {
        var linkedAt = Instant.parse("2026-01-01T00:00:00Z");
        var original = PartyIdentityAssociation.link(UUID.randomUUID(),
                TenantId.generate(), PartyId.generate(),
                new IdentitySubjectRef("numou-user", "user-123"), linkedAt);

        var revoked = original.revoke(linkedAt.plusSeconds(60));

        assertTrue(original.isActive());
        assertFalse(revoked.isActive());
        assertEquals(linkedAt.plusSeconds(60), revoked.revokedAt());
        assertEquals(original.id(), revoked.id());
    }

    @Test
    void rejectsRevocationBeforeLinkTime() {
        var linkedAt = Instant.parse("2026-01-01T00:00:00Z");
        var association = PartyIdentityAssociation.link(UUID.randomUUID(),
                TenantId.generate(), PartyId.generate(),
                new IdentitySubjectRef("numou-user", "user-123"), linkedAt);

        assertThrows(IllegalArgumentException.class,
                () -> association.revoke(linkedAt.minusSeconds(1)));
    }

    @Test
    void requiresIssuerAndSubject() {
        assertThrows(IllegalArgumentException.class,
                () -> new IdentitySubjectRef(" ", "subject"));
        assertThrows(IllegalArgumentException.class,
                () -> new IdentitySubjectRef("issuer", " "));
    }

    @Test
    void rejectsRevokedAssociationWithoutRevocationTime() {
        assertThrows(IllegalArgumentException.class,
                () -> new PartyIdentityAssociation(UUID.randomUUID(),
                        TenantId.generate(), PartyId.generate(),
                        new IdentitySubjectRef("issuer", "subject"),
                        PartyIdentityAssociationStatus.REVOKED,
                        Instant.parse("2026-01-01T00:00:00Z"), null));
    }
}
