package tech.kayys.numou.feedback.adapter.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.application.command.SubmitDistributedSurveyResponseHandler;
import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.feedback.application.query.ResolveSurveyDistributionHandler;
import tech.kayys.numou.feedback.domain.survey.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.application.TenantContext;
import tech.kayys.numou.survey.application.port.in.ResponseUseCase;
import tech.kayys.numou.survey.domain.id.*;
import tech.kayys.numou.survey.domain.response.ResponseStatus;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PublicSurveyInvitationResourceTest {
    private static final Instant NOW = Instant.parse("2099-01-01T12:00:00Z");

    @Test
    void startDerivesTenantAndSurveyFromInvitationNotRequestBody() throws Exception {
        FakeRepository repository = new FakeRepository();
        String token = "public-token";
        TenantId tenant = TenantId.generate();
        repository.distribution = new SurveyDistribution(UUID.randomUUID(), tenant, "survey-authoritative",
                DistributionChannel.PUBLIC_LINK, hash(token), NOW.plusSeconds(3600), 20,
                DistributionStatus.ACTIVE, NOW.minusSeconds(1));
        CapturingResponses responses = new CapturingResponses();
        var resolver = new ResolveSurveyDistributionHandler(repository);
        var submissions = new SubmitDistributedSurveyResponseHandler(resolver, repository, (t, s, r) -> { });
        var resource = new PublicSurveyInvitationResource(resolver, responses, submissions);

        var body = new ObjectMapper().readTree("""
                {"tenantId":"attacker-tenant","surveyId":"attacker-survey","respondentId":"respondent-1"}
                """);
        var result = resource.start(token, body);

        assertEquals(201, result.getStatus());
        assertEquals(tenant, responses.context.tenantId());
        assertEquals("survey-authoritative", responses.command.survey().value());
        assertEquals("respondent-1", responses.command.respondentId());
    }

    @Test
    void answerWriteCannotCrossSurveyEvenWithinSameTenant() throws Exception {
        FakeRepository repository = new FakeRepository();
        String token = "public-token";
        repository.distribution = new SurveyDistribution(UUID.randomUUID(), TenantId.generate(), "survey-authoritative",
                DistributionChannel.PUBLIC_LINK, hash(token), NOW.plusSeconds(3600), 20,
                DistributionStatus.ACTIVE, NOW.minusSeconds(1));
        CapturingResponses responses = new CapturingResponses();
        responses.sessionSurvey = "another-survey";
        var resolver = new ResolveSurveyDistributionHandler(repository);
        var submissions = new SubmitDistributedSurveyResponseHandler(resolver, repository, (t, s, r) -> { });
        var resource = new PublicSurveyInvitationResource(resolver, responses, submissions);

        var result = resource.answer(token, "response-1", "question-1",
                new ObjectMapper().readTree("{\\"kind\\":\\"text\\",\\"value\\":\\"hello\\"}"));

        assertEquals(404, result.getStatus());
        assertFalse(responses.answerSaved);
    }

    private static String hash(String token) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static final class CapturingResponses implements ResponseUseCase {
        TenantContext context;
        StartResponse command;
        String sessionSurvey = "survey-authoritative";
        boolean answerSaved;

        @Override public ResponseSession start(TenantContext ctx, StartResponse cmd) {
            context = ctx;
            command = cmd;
            return new ResponseSession(new ResponseId("response-1"), cmd.survey(), ResponseStatus.IN_PROGRESS,
                    1, List.of(), Map.of(), Optional.empty());
        }
        @Override public ResponseSession saveAnswer(TenantContext ctx, SaveAnswer cmd) {
            answerSaved = true;
            return session();
        }
        @Override public ResponseSession getSession(TenantContext ctx, ResponseId id) { return session(); }
        private ResponseSession session() {
            return new ResponseSession(new ResponseId("response-1"), new SurveyId(sessionSurvey),
                    ResponseStatus.IN_PROGRESS, 1, List.of(), Map.of(), Optional.empty());
        }
        @Override public SubmissionResult submit(TenantContext ctx, ResponseId id) { throw new UnsupportedOperationException(); }
    }

    private static final class FakeRepository implements SurveyDistributionRepository {
        SurveyDistribution distribution;
        @Override public void save(SurveyDistribution value) { distribution = value; }
        @Override public Optional<SurveyDistribution> findById(TenantId tenant, UUID id) {
            return distribution != null && distribution.tenantId().equals(tenant) && distribution.id().equals(id)
                    ? Optional.of(distribution) : Optional.empty();
        }
        @Override public Optional<SurveyDistribution> findActiveByTokenHash(String tokenHash) {
            return distribution != null && distribution.tokenHash().equals(tokenHash)
                    && distribution.status() == DistributionStatus.ACTIVE && distribution.expiresAt().isAfter(NOW)
                    ? Optional.of(distribution) : Optional.empty();
        }
        @Override public ReservationResult reserveResponse(TenantId tenant, UUID id, String response, Instant now) {
            return ReservationResult.RESERVED;
        }
        @Override public void completeResponse(TenantId tenant, UUID id, String response, Instant now) {}
        @Override public void releaseResponse(TenantId tenant, UUID id, String response, Instant now) {}
        @Override public boolean revoke(TenantId tenant, UUID id, Instant at) { return false; }
    }
}
