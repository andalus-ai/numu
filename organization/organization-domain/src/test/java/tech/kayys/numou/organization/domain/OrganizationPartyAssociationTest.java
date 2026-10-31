package tech.kayys.numou.organization.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationPartyAssociationTest {
    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void enforcesLifecycleAndPreservesTypedIdentifiers() {
        OrganizationId organizationId = OrganizationId.generate();
        PartyId partyId = PartyId.generate();
        var proposed = OrganizationPartyAssociation.propose(
                UUID.randomUUID(), organizationId, partyId, T0, "operator-1", "registry:record-7");

        assertEquals(OrganizationPartyAssociationStatus.PENDING_VERIFICATION, proposed.status());
        assertEquals(organizationId, proposed.organizationId());
        assertEquals(partyId, proposed.partyId());
        assertThrows(IllegalStateException.class, () -> proposed.suspend(T0.plusSeconds(1), "operator-1", "hold"));

        var active = proposed.activate(T0.plusSeconds(1), "verifier-1");
        assertEquals(OrganizationPartyAssociationStatus.ACTIVE, active.status());
        assertEquals(1, active.version());
        assertEquals(T0.plusSeconds(1), active.verifiedAt());

        var suspended = active.suspend(T0.plusSeconds(2), "operator-2", "evidence review");
        var reinstated = suspended.reinstate(T0.plusSeconds(3), "verifier-2");
        assertEquals(OrganizationPartyAssociationStatus.ACTIVE, reinstated.status());
        assertEquals(3, reinstated.version());

        var revoked = reinstated.revoke(T0.plusSeconds(4), "operator-3", "incorrect association");
        assertEquals(OrganizationPartyAssociationStatus.REVOKED, revoked.status());
        assertEquals(T0.plusSeconds(4), revoked.closedAt());
        assertThrows(IllegalStateException.class,
                () -> revoked.activate(T0.plusSeconds(5), "operator-4"));
    }

    @Test
    void requiresReasonForSuspensionAndTerminalCorrection() {
        var proposed = OrganizationPartyAssociation.propose(
                UUID.randomUUID(), OrganizationId.generate(), PartyId.generate(), T0, "operator", null);
        var active = proposed.activate(T0.plusSeconds(1), "verifier");
        assertThrows(IllegalArgumentException.class,
                () -> active.suspend(T0.plusSeconds(2), "operator", " "));
        assertThrows(IllegalArgumentException.class,
                () -> active.supersede(T0.plusSeconds(2), "operator", null));
        assertThrows(IllegalArgumentException.class,
                () -> active.revoke(T0.plusSeconds(2), "operator", ""));
    }

    @Test
    void suspendedAssociationStillReservesItsIdentity() {
        assertTrue(OrganizationPartyAssociationStatus.SUSPENDED.reservesIdentity());
        assertTrue(OrganizationPartyAssociationStatus.PENDING_VERIFICATION.reservesIdentity());
        assertFalse(OrganizationPartyAssociationStatus.REVOKED.reservesIdentity());
        assertFalse(OrganizationPartyAssociationStatus.SUPERSEDED.reservesIdentity());
    }
}
