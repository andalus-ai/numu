package tech.kayys.numou.affiliate.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AffiliateLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-10-28T00:00:00Z");

    @Test
    void programActivationAndSuspensionAreVersionedTransitions() {
        AffiliateProgram draft = AffiliateProgram.draft(UUID.randomUUID(), "tenant-a", "  Launch Partners ", NOW);
        assertEquals("Launch Partners", draft.name());
        AffiliateProgram active = draft.activate(NOW.plusSeconds(1));
        assertEquals(ProgramStatus.ACTIVE, active.status());
        assertEquals(1, active.version());
        assertEquals(ProgramStatus.DRAFT, draft.status(), "old snapshot remains unchanged");
        assertEquals(ProgramStatus.SUSPENDED, active.suspend(NOW.plusSeconds(2)).status());
    }

    @Test
    void activeProgramCannotBeArchivedWithoutSuspension() {
        AffiliateProgram active = AffiliateProgram.draft(UUID.randomUUID(), "tenant-a", "Program", NOW)
                .activate(NOW.plusSeconds(1));
        assertThrows(IllegalStateException.class, () -> active.archive(NOW.plusSeconds(2)));
    }

    @Test
    void partnerMustBeApprovedBeforeSuspension() {
        AffiliatePartner partner = AffiliatePartner.register(UUID.randomUUID(), "tenant-a", "Partner", NOW);
        assertThrows(IllegalStateException.class, () -> partner.suspend(NOW.plusSeconds(1)));
        assertEquals(PartnerStatus.ACTIVE, partner.approve(NOW.plusSeconds(1)).status());
    }

    @Test
    void referralCodeIsNormalizedAndStrictlyValidated() {
        AffiliateReferralLink link = AffiliateReferralLink.issue(
                UUID.randomUUID(), "tenant-a", UUID.randomUUID(), UUID.randomUUID(), "  Spring_26 ", NOW);
        assertEquals("spring_26", link.code());
        assertThrows(IllegalArgumentException.class, () -> AffiliateReferralLink.normalizeCode("x"));
        assertThrows(IllegalArgumentException.class, () -> AffiliateReferralLink.normalizeCode("https://evil.test"));
    }

    @Test
    void linkDisableIsIdempotentAndVersionedOnce() {
        AffiliateReferralLink link = AffiliateReferralLink.issue(
                UUID.randomUUID(), "tenant-a", UUID.randomUUID(), UUID.randomUUID(), "partner-123", NOW);
        AffiliateReferralLink disabled = link.disable(NOW.plusSeconds(1));
        assertEquals(1, disabled.version());
        assertSame(disabled, disabled.disable(NOW.plusSeconds(2)));
        assertEquals(ReferralLinkStatus.ACTIVE, link.status());
    }

    @Test
    void commandContextRequiresTenantActorAndIdempotency() {
        assertThrows(IllegalArgumentException.class,
                () -> new tech.kayys.numou.affiliate.application.AffiliateCommandContext(
                        " ", "operator", "key", "corr"));
    }
}
