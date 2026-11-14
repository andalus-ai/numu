package tech.kayys.numou.feedback.application.service;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.feedback.application.command.SubmitDistributedSurveyResponseHandler;
import tech.kayys.numou.feedback.application.port.DistributedSurveyResponsePort;
import tech.kayys.numou.feedback.application.port.PublicSurveyAccessPort;
import tech.kayys.numou.feedback.application.port.SurveyDistributionRepository;
import tech.kayys.numou.feedback.application.query.ResolveSurveyDistributionHandler;
import tech.kayys.numou.feedback.domain.survey.*;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PublicSurveyInvitationServiceTest {
    private static final Instant NOW = Instant.parse("2099-01-01T12:00:00Z");
    private static final String TOKEN = "valid-public-invitation-token";

    @Test
    void publicOperationsAreBoundToInvitationTenantAndSurvey() {
        FakeRepository repository = new FakeRepository();
        SurveyDistribution invitation = new SurveyDistribution(UUID.randomUUID(), TenantId.generate(),
                "survey-42", DistributionChannel.PUBLIC_LINK, sha256(TOKEN),
                NOW.plusSeconds(3600), 5, DistributionStatus.ACTIVE, NOW.minusSeconds(60));
        repository.distribution = invitation;

        FakeSurvey survey = new FakeSurvey();
        int[] submissions = {0};
        DistributedSurveyResponsePort submissionPort = (tenant, surveyId, responseId) -> {
            assertEquals(invitation.tenantId(), tenant);
            assertEquals("survey-42", surveyId);
            assertEquals("response-1", responseId);
            submissions[0]++;
        };
        var resolver = new ResolveSurveyDistributionHandler(repository);
        var submitter = new SubmitDistributedSurveyResponseHandler(resolver, repository, submissionPort);
        var service = new PublicSurveyInvitationService(resolver, survey, submitter);

        assertEquals("survey-42", service.describe(TOKEN).surveyId());
        assertEquals("response-1", service.start(TOKEN, null).responseId());
        assertEquals("response-1", service.getSession(TOKEN, "response-1").responseId());
        assertEquals("response-1", service.saveAnswer(TOKEN, "response-1", "question-1",
                Map.of("text", "hello")).responseId());
        assertEquals(SubmitDistributedSurveyResponseHandler.Outcome.SUBMITTED,
                service.submit(TOKEN, "response-1", NOW));
        assertEquals(1, submissions[0]);
    }

    @Test
    void rejectsUnknownTokensAndResponseIdsFromOtherSurveys() {
        FakeRepository repository = new FakeRepository();
        repository.distribution = new SurveyDistribution(UUID.randomUUID(), TenantId.generate(),
                "survey-42", DistributionChannel.PUBLIC_LINK, sha256(TOKEN),
                NOW.plusSeconds(3600), 5, DistributionStatus.ACTIVE, NOW.minusSeconds(60));
        FakeSurvey survey = new FakeSurvey();
        survey.session = snapshot("response-other", "different-survey");
        var resolver = new ResolveSurveyDistributionHandler(repository);
        var service = new PublicSurveyInvitationService(resolver, survey,
                new SubmitDistributedSurveyResponseHandler(resolver, repository, (t, s, r) -> { }));

        assertThrows(SubmitDistributedSurveyResponseHandler.InvalidSurveyInvitationException.class,
                () -> service.describe("unknown-token"));
        assertThrows(SubmitDistributedSurveyResponseHandler.InvalidSurveyInvitationException.class,
                () -> service.getSession(TOKEN, "response-other"));
    }

    @Test
    void rejectsSessionReturnedForAnotherSurveyDuringStart() {
        FakeRepository repository = new FakeRepository();
        repository.distribution = new SurveyDistribution(UUID.randomUUID(), TenantId.generate(),
                "survey-42", DistributionChannel.PUBLIC_LINK, sha256(TOKEN),
                NOW.plusSeconds(3600), 5, DistributionStatus.ACTIVE, NOW.minusSeconds(60));
        FakeSurvey survey = new FakeSurvey();
        survey.session = snapshot("response-other", "different-survey");
        var resolver = new ResolveSurveyDistributionHandler(repository);
        var service = new PublicSurveyInvitationService(resolver, survey,
                new SubmitDistributedSurveyResponseHandler(resolver, repository, (t, s, r) -> { }));

        assertThrows(SubmitDistributedSurveyResponseHandler.InvalidSurveyInvitationException.class,
                () -> service.start(TOKEN, null));
    }

    @Test
    void cannotSaveAnAnswerForAQuestionNotVisibleInTheCurrentSession() {
        FakeRepository repository = new FakeRepository();
        repository.distribution = new SurveyDistribution(UUID.randomUUID(), TenantId.generate(),
                "survey-42", DistributionChannel.PUBLIC_LINK, sha256(TOKEN),
                NOW.plusSeconds(3600), 5, DistributionStatus.ACTIVE, NOW.minusSeconds(60));
        FakeSurvey survey = new FakeSurvey();
        var resolver = new ResolveSurveyDistributionHandler(repository);
        var service = new PublicSurveyInvitationService(resolver, survey,
                new SubmitDistributedSurveyResponseHandler(resolver, repository, (t, s, r) -> { }));

        assertThrows(IllegalArgumentException.class,
                () -> service.saveAnswer(TOKEN, "response-1", "hidden-question", Map.of("text", "x")));
    }

    private static PublicSurveyAccessPort.ResponseSnapshot snapshot(String responseId, String surveyId) {
        return new PublicSurveyAccessPort.ResponseSnapshot(responseId, surveyId, "IN_PROGRESS", 1,
                List.of(new PublicSurveyAccessPort.QuestionSnapshot(
                        "question-1", "Question", "", true, "short_text", Map.of("maxLength", 100))),
                Map.of(), null);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static final class FakeSurvey implements PublicSurveyAccessPort {
        private ResponseSnapshot session = snapshot("response-1", "survey-42");
        @Override public SurveyDescriptor describe(TenantId tenant, String surveyId) {
            assertEquals("survey-42", surveyId);
            return new SurveyDescriptor(surveyId, "Survey", "", "PUBLISHED", true, "Thanks",
                    session.questions().size());
        }
        @Override public ResponseSnapshot start(TenantId tenant, String surveyId, String respondentId) {
            assertEquals("survey-42", surveyId);
            return session;
        }
        @Override public ResponseSnapshot getSession(TenantId tenant, String responseId) {
            return session;
        }
        @Override public ResponseSnapshot saveAnswer(TenantId tenant, String responseId,
                String questionId, Map<String, Object> answer) {
            assertEquals("question-1", questionId);
            return session;
        }
    }

    private static final class FakeRepository implements SurveyDistributionRepository {
        private SurveyDistribution distribution;
        private boolean completed;
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
        @Override public ReservationResult reserveResponse(TenantId tenant, UUID id, String responseId, Instant now) {
            if (distribution == null || !distribution.tenantId().equals(tenant) || !distribution.id().equals(id))
                return ReservationResult.NOT_ACTIVE;
            if (completed) return ReservationResult.ALREADY_COMPLETED;
            completed = true;
            return ReservationResult.RESERVED;
        }
        @Override public void completeResponse(TenantId tenant, UUID id, String responseId, Instant now) { }
        @Override public void releaseResponse(TenantId tenant, UUID id, String responseId, Instant now) { completed = false; }
        @Override public boolean revoke(TenantId tenant, UUID id, Instant at) { return false; }
    }
}