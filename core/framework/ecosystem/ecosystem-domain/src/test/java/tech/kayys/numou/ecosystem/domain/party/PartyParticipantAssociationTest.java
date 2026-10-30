package tech.kayys.numou.ecosystem.domain.party;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PartyParticipantAssociationTest {
    private static final Instant START = Instant.parse("2026-10-30T00:00:00Z");

    @Test
    void establishesTenantScopedBusinessRoleWithoutCreatingAuthorization() {
        var association = PartyParticipantAssociation.establish(UUID.randomUUID(), TenantId.generate(),
                PartyId.generate(), ParticipantId.generate(), PartyParticipantRole.CUSTOMER, START);

        assertEquals(PartyParticipantAssociationStatus.ACTIVE, association.status());
        assertTrue(association.isActiveAt(START));
        assertFalse(association.isActiveAt(START.minusSeconds(1)));
        assertNull(association.revokedAt());
    }

    @Test
    void revocationIsImmutableAndPreservesRelationshipIdentity() {
        var original = PartyParticipantAssociation.establish(UUID.randomUUID(), TenantId.generate(),
                PartyId.generate(), ParticipantId.generate(), PartyParticipantRole.PARTNER, START);
        var revoked = original.revoke(START.plusSeconds(60));

        assertEquals(PartyParticipantAssociationStatus.ACTIVE, original.status());
        assertEquals(PartyParticipantAssociationStatus.REVOKED, revoked.status());
        assertEquals(original.id(), revoked.id());
        assertEquals(original.partyId(), revoked.partyId());
        assertEquals(original.participantId(), revoked.participantId());
        assertFalse(revoked.isActiveAt(START.plusSeconds(120)));
        assertSame(revoked, revoked.revoke(START.plusSeconds(120)));
    }

    @Test
    void rejectsInvalidLifecycleTimestamps() {
        assertThrows(IllegalArgumentException.class, () -> new PartyParticipantAssociation(
                UUID.randomUUID(), TenantId.generate(), PartyId.generate(), ParticipantId.generate(),
                PartyParticipantRole.CUSTOMER, PartyParticipantAssociationStatus.ACTIVE, START, START));
        var active = PartyParticipantAssociation.establish(UUID.randomUUID(), TenantId.generate(),
                PartyId.generate(), ParticipantId.generate(), PartyParticipantRole.CUSTOMER, START);
        assertThrows(IllegalArgumentException.class, () -> active.revoke(START.minusSeconds(1)));
    }

    @Test
    void partyAndParticipantRemainSeparateStronglyTypedIdentifiers() {
        assertNotEquals(PartyId.generate().getClass(), ParticipantId.generate().getClass());
    }
}
