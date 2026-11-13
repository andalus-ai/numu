package tech.kayys.numou.feedback.domain.survey;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SurveyDistributionTest {
    private static final Instant NOW = Instant.parse("2026-11-11T12:00:00Z");

    @Test
    void invitationExpiresAndHonorsResponseLimit() {
        var grant = new SurveyDistribution(UUID.randomUUID(), TenantId.generate(), "survey-123",
                DistributionChannel.QR_CODE, "a".repeat(64), NOW.plusSeconds(3600),
                10, DistributionStatus.ACTIVE, NOW);

        assertTrue(grant.acceptsAt(NOW, 0));
        assertTrue(grant.acceptsAt(NOW, 9));
        assertFalse(grant.acceptsAt(NOW, 10));
        assertFalse(grant.acceptsAt(NOW.plusSeconds(3600), 0));
    }

    @Test
    void revokedOrMalformedGrantsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SurveyDistribution(
                UUID.randomUUID(), TenantId.generate(), "survey-123", DistributionChannel.PUBLIC_LINK,
                "not-a-hash", NOW.plusSeconds(60), 10, DistributionStatus.ACTIVE, NOW));
        var revoked = new SurveyDistribution(UUID.randomUUID(), TenantId.generate(), "survey-123",
                DistributionChannel.EMAIL, "b".repeat(64), NOW.plusSeconds(60),
                10, DistributionStatus.REVOKED, NOW);
        assertFalse(revoked.acceptsAt(NOW, 0));
    }
}
