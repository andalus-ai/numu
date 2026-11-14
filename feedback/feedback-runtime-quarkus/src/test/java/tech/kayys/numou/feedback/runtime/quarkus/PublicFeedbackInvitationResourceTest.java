package tech.kayys.numou.feedback.runtime.quarkus;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.application.command.SubmitDistributedSurveyResponseHandler;
import tech.kayys.numou.feedback.application.port.PublicSurveyAccessPort;
import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.feedback.application.query.ResolveSurveyDistributionHandler;
import tech.kayys.numou.feedback.application.service.PublicInvitationRateLimiter;
import tech.kayys.numou.feedback.application.service.PublicSurveyInvitationService;
import tech.kayys.numou.feedback.domain.survey.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PublicFeedbackInvitationResourceTest {
    private static final Instant NOW = Instant.parse("2099-01-01T12:00:00Z");
    private static final String TOKEN = "public-invitation-token";
    private static final String SURVEY_ID = "survey-public-1";

    @Test
    void metadataAndStartAreNoStoreAndUseResolvedInvitation() {
        Fixture fixture = new Fixture();
        var metadata = fixture.resource.describe(TOKEN);
        assertEquals(200, metadata.getStatus());
        assertEquals("no-store, private", metadata.getHeaderString("Cache-Control"));
        assertEquals("nosniff", metadata.getHeaderString("X-Content-Type-Options"));

        var started = fixture.resource.start(TOKEN,
                new PublicFeedbackInvitationResource.StartRequest(null));
        assertEquals(201, started.getStatus());
        assertEquals("no-store, private", started.getHeaderString("Cache-Control"));
        assertInstanceOf(PublicSurveyAccessPort.ResponseSnapshot.class, started.getEntity());
    }

    @Test
    void invalidTokenIsRejectedBeforeCreatingRateLimitBucket() {
        Fixture fixture = new Fixture();
        assertThrows(SubmitDistributedSurveyResponseHandler.InvalidSurveyInvitationException.class,
                () -> fixture.resource.describe("not-a-token"));
        assertEquals(0, fixture.rateLimitCalls);
    }

    @Test
    void disabledEndpointIsNotDisclosed() {
        Fixture fixture = new Fixture(false);
        assertThrows(jakarta.ws.rs.NotFoundException.class,
                () -> fixture.resource.describe(TOKEN));
        assertEquals(0, fixture.rateLimitCalls);
    }

    private static final class Fixture {
        private int rateLimitCalls;
        private final PublicFeedbackInvitationResource resource;

        private Fixture() { this(true); }

        private Fixture(boolean enabled) {
            FakeRepository repository = new FakeRepository();
            repository.distribution = new SurveyDistribution(UUID.randomUUID(), TenantId.generate(),
                    SURVEY_ID, DistributionChannel.PUBLIC_LINK, sha256(TOKEN),
                    NOW.plusSeconds(3600), 10, DistributionStatus.ACTIVE, NOW.minusSeconds(60));
            var resolver = new ResolveSurveyDistributionHandler(repository);
            PublicSurveyAccessPort access = new PublicSurveyAccessPort() {
                @Override public SurveyDescriptor describe(TenantId tenant, String surveyId) {
                    assertEquals(SURVEY_ID, surveyId);
                    return new SurveyDescriptor(surveyId, "Public survey", "", "PUBLISHED",
                            true, "Thank you", 0);
                }
                @Override public ResponseSnapshot start(TenantId tenant, String surveyId, String respondentId) {
                    return snapshot("response-1", surveyId);
                }
                @Override public ResponseSnapshot getSession(TenantId tenant, String responseId) {
                    return snapshot(responseId, SURVEY_ID);
                }
                @Override public ResponseSnapshot saveAnswer(TenantId tenant, String responseId,
                        String questionId, Map<String, Object> answer) {
                    return snapshot(responseId, SURVEY_ID);
                }
            };
            var submitter = new SubmitDistributedSurveyResponseHandler(resolver, repository, (t, s, r) -> { });
            var service = new PublicSurveyInvitationService(resolver, access, submitter);
            var limiter = new PublicInvitationRateLimiter((hash, window, limit) -> {
                rateLimitCalls++;
                return true;
            }, 60);
            resource = new PublicFeedbackInvitationResource(service, limiter,
                    Clock.fixed(NOW, ZoneOffset.UTC), enabled);
        }
    }

    private static PublicSurveyAccessPort.ResponseSnapshot snapshot(String responseId, String surveyId) {
        return new PublicSurveyAccessPort.ResponseSnapshot(responseId, surveyId, "IN_PROGRESS",
                1, List.of(), Map.of(), null);
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static final class FakeRepository implements SurveyDistributionRepository {
        private SurveyDistribution distribution;
        private boolean reserved;
        @Override public void save(SurveyDistribution value) { distribution = value; }
        @Override public Optional<SurveyDistribution> findById(TenantId tenant, UUID id) {
            return distribution != null && distribution.tenantId().equals(tenant)
                    && distribution.id().equals(id) ? Optional.of(distribution) : Optional.empty();
        }
        @Override public Optional<SurveyDistribution> findActiveByTokenHash(String hash) {
            return distribution != null && distribution.tokenHash().equals(hash)
                    && distribution.status() == DistributionStatus.ACTIVE
                    && distribution.expiresAt().isAfter(NOW) ? Optional.of(distribution) : Optional.empty();
        }
        @Override public ReservationResult reserveResponse(TenantId tenant, UUID id, String response, Instant now) {
            if (reserved) return ReservationResult.ALREADY_COMPLETED;
            reserved = true;
            return ReservationResult.RESERVED;
        }
        @Override public void completeResponse(TenantId tenant, UUID id, String response, Instant now) { }
        @Override public void releaseResponse(TenantId tenant, UUID id, String response, Instant now) { reserved = false; }
        @Override public boolean revoke(TenantId tenant, UUID id, Instant at) { return false; }
    }
}