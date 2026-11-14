package tech.kayys.numou.feedback.application.command;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.application.port.DistributedSurveyResponsePort;
import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.feedback.application.query.ResolveSurveyDistributionHandler;
import tech.kayys.numou.feedback.domain.survey.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SubmitDistributedSurveyResponseHandlerTest {
    private static final Instant NOW = Instant.parse("2099-01-01T12:00:00Z");

    @Test
    void reservesThenCompletesExactlyOnce() {
        FakeRepository repository = new FakeRepository();
        String token = createInvitation(repository, 1);
        int[] submissions = {0};
        var handler = new SubmitDistributedSurveyResponseHandler(
                new ResolveSurveyDistributionHandler(repository), repository,
                (tenant, survey, response) -> {
                    assertEquals("survey-1", survey);
                    submissions[0]++;
                });

        assertEquals(SubmitDistributedSurveyResponseHandler.Outcome.SUBMITTED,
                handler.handle(token, "response-1", NOW));
        assertEquals(SubmitDistributedSurveyResponseHandler.Outcome.ALREADY_SUBMITTED,
                handler.handle(token, "response-1", NOW));
        assertEquals(1, submissions[0]);
        assertEquals(1, repository.accepted);
        assertEquals(0, repository.reserved);
    }

    @Test
    void failedSurveySubmissionReleasesReservation() {
        FakeRepository repository = new FakeRepository();
        String token = createInvitation(repository, 1);
        var handler = new SubmitDistributedSurveyResponseHandler(
                new ResolveSurveyDistributionHandler(repository), repository,
                (tenant, survey, response) -> { throw new IllegalStateException("survey rejected response"); });

        assertThrows(IllegalStateException.class, () -> handler.handle(token, "response-2", NOW));
        assertEquals(0, repository.accepted);
        assertEquals(0, repository.reserved);
        assertEquals(SurveyDistributionRepository.ReservationResult.RESERVED,
                repository.reserveResponse(repository.distribution.tenantId(), repository.distribution.id(),
                        "response-3", NOW));
    }

    @Test
    void rejectsUnknownOrExpiredInvitationAndEnforcesLimit() {
        FakeRepository repository = new FakeRepository();
        String token = createInvitation(repository, 1);
        var handler = new SubmitDistributedSurveyResponseHandler(
                new ResolveSurveyDistributionHandler(repository), repository, (t, s, r) -> { });

        handler.handle(token, "response-4", NOW);
        assertThrows(SubmitDistributedSurveyResponseHandler.SurveyDistributionLimitReachedException.class,
                () -> handler.handle(token, "response-5", NOW));
        assertThrows(SubmitDistributedSurveyResponseHandler.InvalidSurveyInvitationException.class,
                () -> handler.handle("unknown", "response-6", NOW));
    }

    private static String createInvitation(FakeRepository repository, int maxResponses) {
        String token = "test-invitation-token";
        repository.distribution = new SurveyDistribution(UUID.randomUUID(), TenantId.generate(), "survey-1",
                DistributionChannel.PUBLIC_LINK, sha256(token), NOW.plusSeconds(3600), maxResponses,
                DistributionStatus.ACTIVE, NOW.minusSeconds(1));
        return token;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static final class FakeRepository implements SurveyDistributionRepository {
        private SurveyDistribution distribution;
        private int accepted;
        private int reserved;
        private final java.util.Map<String, String> claims = new java.util.HashMap<>();

        @Override public void save(SurveyDistribution value) { distribution = value; }
        @Override public Optional<SurveyDistribution> findById(TenantId tenant, UUID id) {
            return distribution != null && distribution.tenantId().equals(tenant) && distribution.id().equals(id)
                    ? Optional.of(distribution) : Optional.empty();
        }
        @Override public Optional<SurveyDistribution> findActiveByTokenHash(String hash) {
            return distribution != null && distribution.tokenHash().equals(hash)
                    && distribution.status() == DistributionStatus.ACTIVE
                    && distribution.expiresAt().isAfter(NOW) ? Optional.of(distribution) : Optional.empty();
        }
        @Override public ReservationResult reserveResponse(TenantId tenant, UUID id, String response, Instant now) {
            if (distribution == null || !distribution.tenantId().equals(tenant) || !distribution.id().equals(id)
                    || distribution.status() != DistributionStatus.ACTIVE || !distribution.expiresAt().isAfter(now))
                return ReservationResult.NOT_ACTIVE;
            String status = claims.get(response);
            if ("COMPLETED".equals(status)) return ReservationResult.ALREADY_COMPLETED;
            if ("RESERVED".equals(status)) return ReservationResult.ALREADY_RESERVED;
            if (accepted + reserved >= distribution.maxResponses()) return ReservationResult.LIMIT_REACHED;
            reserved++;
            claims.put(response, "RESERVED");
            return ReservationResult.RESERVED;
        }
        @Override public void completeResponse(TenantId tenant, UUID id, String response, Instant now) {
            if ("COMPLETED".equals(claims.get(response))) return;
            if (!"RESERVED".equals(claims.get(response))) throw new IllegalStateException();
            reserved--; accepted++; claims.put(response, "COMPLETED");
        }
        @Override public void releaseResponse(TenantId tenant, UUID id, String response, Instant now) {
            if (!"RESERVED".equals(claims.get(response))) return;
            reserved--; claims.put(response, "RELEASED");
        }
        @Override public boolean revoke(TenantId tenant, UUID id, Instant at) { return false; }
    }
}
