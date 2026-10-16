package tech.kayys.syirkah.ecosystem.domain.contact;

import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.ecosystem.domain.party.PartyId;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ContactDomainTest {

    @Test
    void shouldCreateAndValidatePostalAddress() {
        PostalAddress address = new PostalAddress(
                "  Jl. Sudirman No. 1  ",
                " Suite 500 ",
                " Jakarta ",
                " DKI Jakarta ",
                " 10220 ",
                " id "
        );

        assertEquals("Jl. Sudirman No. 1", address.addressLine1());
        assertEquals("Suite 500", address.addressLine2());
        assertEquals("Jakarta", address.locality());
        assertEquals("DKI Jakarta", address.administrativeArea());
        assertEquals("10220", address.postalCode());
        assertEquals("ID", address.countryCode());

        // Invalid country code
        assertThrows(IllegalArgumentException.class, () ->
                new PostalAddress("Line 1", null, "City", null, "123", "IDN"));
        assertThrows(IllegalArgumentException.class, () ->
                new PostalAddress("Line 1", null, "City", null, "123", ""));
    }

    @Test
    void shouldManageContactPointLifecycleAndVerification() {
        ContactPointId id = ContactPointId.generate();
        ContactPoint cp = ContactPoint.create(id, ContactPointKind.EMAIL, "info@syirkah.tech");

        assertEquals(ContactPointStatus.ACTIVE, cp.status());
        assertEquals(VerificationStatus.UNVERIFIED, cp.verificationStatus());

        ContactPoint verified = cp.withVerificationStatus(VerificationStatus.VERIFIED);
        assertEquals(VerificationStatus.VERIFIED, verified.verificationStatus());

        // Modifying value must reset verification to UNVERIFIED (invariant #6)
        ContactPoint updated = verified.withValue("new@syirkah.tech");
        assertEquals("new@syirkah.tech", updated.value());
        assertEquals(VerificationStatus.UNVERIFIED, updated.verificationStatus());
    }

    @Test
    void shouldEvaluateEffectivePeriodCorrectly() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        EffectivePeriod period = EffectivePeriod.of(start, end);

        assertTrue(period.contains(LocalDate.of(2026, 1, 1)));
        assertTrue(period.contains(LocalDate.of(2026, 6, 15)));
        assertTrue(period.contains(LocalDate.of(2026, 12, 31)));
        assertFalse(period.contains(LocalDate.of(2025, 12, 31)));
        assertFalse(period.contains(LocalDate.of(2027, 1, 1)));

        // Inverted dates rejected
        assertThrows(IllegalArgumentException.class, () -> EffectivePeriod.of(end, start));

        // Overlap checks
        EffectivePeriod overlapping = EffectivePeriod.of(LocalDate.of(2026, 6, 1), LocalDate.of(2027, 6, 1));
        assertTrue(period.overlaps(overlapping));

        EffectivePeriod nonOverlapping = EffectivePeriod.of(LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
        assertFalse(period.overlaps(nonOverlapping));
    }

    @Test
    void shouldAssociatePartyAddressAndContactPoint() {
        PartyId partyId = PartyId.generate();
        AddressId addressId = AddressId.generate();
        ContactPointId contactPointId = ContactPointId.generate();
        EffectivePeriod period = EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1));

        PartyAddress partyAddress = PartyAddress.of(partyId, addressId, ContactPurpose.BILLING, true, period);
        assertTrue(partyAddress.primary());
        assertEquals(ContactPurpose.BILLING, partyAddress.purpose());

        PartyContactPoint partyContact = PartyContactPoint.of(partyId, contactPointId, ContactPurpose.SUPPORT, false, period);
        assertFalse(partyContact.primary());
        assertEquals(ContactPurpose.SUPPORT, partyContact.purpose());
    }
}
