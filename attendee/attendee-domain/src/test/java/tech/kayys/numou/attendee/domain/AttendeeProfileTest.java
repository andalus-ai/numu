package tech.kayys.numou.attendee.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AttendeeProfileTest {
    private static final Instant NOW = Instant.parse("2026-11-05T00:00:00Z");

    private AttendeeProfile pending() {
        return AttendeeProfile.create(UUID.randomUUID(), TenantId.generate(), PartyId.generate(),
                "EVENT_GUEST", "  Ada Lovelace  ", null, "en", NOW);
    }

    @Test
    void createsPendingProfileLinkedToCanonicalPartyWithoutCopyingIdentity() {
        var profile = pending();
        assertEquals(AttendeeProfileStatus.PENDING, profile.status());
        assertEquals("Ada Lovelace", profile.displayName());
        assertEquals("en", profile.preferredLanguage());
        assertNull(profile.preferredName());
        assertEquals(0, profile.revision());
        assertNotNull(profile.partyId());
        assertNotNull(profile.tenantId());
    }

    @Test
    void lifecycleTransitionsAreImmutableAndAdvanceRevision() {
        var pending = pending();
        var active = pending.activate(NOW.plusSeconds(1));
        var suspended = active.suspend(NOW.plusSeconds(2));
        var resumed = suspended.activate(NOW.plusSeconds(3));
        var archived = resumed.archive(NOW.plusSeconds(4));

        assertEquals(AttendeeProfileStatus.PENDING, pending.status());
        assertEquals(AttendeeProfileStatus.ACTIVE, active.status());
        assertEquals(AttendeeProfileStatus.SUSPENDED, suspended.status());
        assertEquals(AttendeeProfileStatus.ACTIVE, resumed.status());
        assertEquals(AttendeeProfileStatus.ARCHIVED, archived.status());
        assertEquals(4, archived.revision());
    }

    @Test
    void rejectsInvalidTransitionsAndArchivedMutation() {
        var profile = pending();
        assertThrows(IllegalStateException.class, () -> profile.suspend(NOW.plusSeconds(1)));
        var archived = profile.archive(NOW.plusSeconds(1));
        assertThrows(IllegalStateException.class, () -> archived.activate(NOW.plusSeconds(2)));
        assertThrows(IllegalStateException.class,
                () -> archived.updatePresentation("New", null, null, NOW.plusSeconds(2)));
    }

    @Test
    void requiresTenantPartyAndValidTimestamps() {
        assertThrows(NullPointerException.class, () -> new AttendeeProfile(
                UUID.randomUUID(), null, PartyId.generate(), "EVENT", "Guest", null, null,
                AttendeeProfileStatus.PENDING, NOW, NOW, 0));
        assertThrows(IllegalArgumentException.class, () -> AttendeeProfile.create(
                UUID.randomUUID(), TenantId.generate(), PartyId.generate(), " ", "Guest",
                null, null, NOW));
        assertThrows(IllegalArgumentException.class, () -> new AttendeeProfile(
                UUID.randomUUID(), TenantId.generate(), PartyId.generate(), "EVENT", "Guest",
                null, null, AttendeeProfileStatus.PENDING, NOW, NOW.minusSeconds(1), 0));
    }


    @Test
    void rejectsTextLengthsThatExceedThePersistenceContract() {
        assertThrows(IllegalArgumentException.class, () -> AttendeeProfile.create(
                UUID.randomUUID(), TenantId.generate(), PartyId.generate(),
                "K".repeat(81), "Guest", null, null, NOW));
        assertThrows(IllegalArgumentException.class, () -> AttendeeProfile.create(
                UUID.randomUUID(), TenantId.generate(), PartyId.generate(),
                "EVENT", "G".repeat(241), null, null, NOW));
        assertThrows(IllegalArgumentException.class, () -> AttendeeProfile.create(
                UUID.randomUUID(), TenantId.generate(), PartyId.generate(),
                "EVENT", "Guest", null, "x".repeat(36), NOW));
    }

    @Test
    void presentationUpdatesPreserveIdentityAndAdvanceRevision() {
        var original = pending();
        var changed = original.updatePresentation("Ada B. Lovelace", "Ada", "id-ID", NOW.plusSeconds(1));
        assertEquals(original.id(), changed.id());
        assertEquals(original.partyId(), changed.partyId());
        assertEquals(original.tenantId(), changed.tenantId());
        assertEquals("Ada B. Lovelace", changed.displayName());
        assertEquals("Ada", changed.preferredName());
        assertEquals("id-ID", changed.preferredLanguage());
        assertEquals(1, changed.revision());
    }

    @Test
    void normalizesTimestampsToPostgresMicrosecondPrecision() {
        var precise = Instant.parse("2026-11-05T00:00:00.123456789Z");
        var profile = AttendeeProfile.create(UUID.randomUUID(), TenantId.generate(),
                PartyId.generate(), "EVENT_GUEST", "Guest", null, null, precise);

        assertEquals(Instant.parse("2026-11-05T00:00:00.123456Z"), profile.createdAt());
        assertEquals(profile.createdAt(), profile.updatedAt());
        var changed = profile.activate(Instant.parse("2026-11-05T00:00:01.987654321Z"));
        assertEquals(Instant.parse("2026-11-05T00:00:01.987654Z"), changed.updatedAt());
    }

    @Test
    void enforcesTextLengthsBeforePersistence() {
        assertThrows(IllegalArgumentException.class, () -> AttendeeProfile.create(
                UUID.randomUUID(), TenantId.generate(), PartyId.generate(),
                "K".repeat(81), "Guest", null, null, NOW));
        assertThrows(IllegalArgumentException.class, () -> AttendeeProfile.create(
                UUID.randomUUID(), TenantId.generate(), PartyId.generate(),
                "EVENT_GUEST", "G".repeat(241), null, null, NOW));
        assertThrows(IllegalArgumentException.class, () -> AttendeeProfile.create(
                UUID.randomUUID(), TenantId.generate(), PartyId.generate(),
                "EVENT_GUEST", "Guest", null, "l".repeat(36), NOW));
    }

}
