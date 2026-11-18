package tech.kayys.numou.feedback.application.service;

import tech.kayys.numou.feedback.application.command.SubmitDistributedSurveyResponseHandler;
import tech.kayys.numou.feedback.application.port.PublicSurveyAccessPort;
import tech.kayys.numou.feedback.application.query.ResolveSurveyDistributionHandler;
import tech.kayys.numou.feedback.domain.survey.SurveyDistribution;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Public respondent façade. Every operation resolves the bearer invitation
 * afresh and checks that the response belongs to the invitation's tenant and survey.
 */
public final class PublicSurveyInvitationService {
    private final ResolveSurveyDistributionHandler resolver;
    private final PublicSurveyAccessPort survey;
    private final SubmitDistributedSurveyResponseHandler submissions;

    public PublicSurveyInvitationService(ResolveSurveyDistributionHandler resolver,
            PublicSurveyAccessPort survey, SubmitDistributedSurveyResponseHandler submissions) {
        this.resolver = Objects.requireNonNull(resolver, "resolver cannot be null");
        this.survey = Objects.requireNonNull(survey, "survey cannot be null");
        this.submissions = Objects.requireNonNull(submissions, "submissions cannot be null");
    }

    public void assertValidInvitation(String token) {
        requireInvitation(token);
    }

    public PublicSurveyAccessPort.SurveyDescriptor describe(String token) {
        SurveyDistribution invitation = requireInvitation(token);
        PublicSurveyAccessPort.SurveyDescriptor descriptor =
                survey.describe(invitation.tenantId(), invitation.surveyId());
        if (!invitation.surveyId().equals(descriptor.surveyId())) {
            throw new SubmitDistributedSurveyResponseHandler.InvalidSurveyInvitationException();
        }
        return descriptor;
    }

    public PublicSurveyAccessPort.ResponseSnapshot start(String token, String respondentId) {
        SurveyDistribution invitation = requireInvitation(token);
        return requireBoundSession(invitation,
                survey.start(invitation.tenantId(), invitation.surveyId(), respondentId));
    }

    public PublicSurveyAccessPort.ResponseSnapshot getSession(String token, String responseId) {
        SurveyDistribution invitation = requireInvitation(token);
        return requireBoundSession(invitation, survey.getSession(invitation.tenantId(), requiredId(responseId)));
    }

    public PublicSurveyAccessPort.ResponseSnapshot saveAnswer(String token, String responseId,
            String questionId, Map<String, Object> answer) {
        SurveyDistribution invitation = requireInvitation(token);
        String normalizedResponseId = requiredId(responseId);
        String normalizedQuestionId = requiredId(questionId);
        PublicSurveyAccessPort.ResponseSnapshot session =
                requireBoundSession(invitation, survey.getSession(invitation.tenantId(), normalizedResponseId));
        boolean visible = session.questions().stream().anyMatch(q -> q.id().equals(normalizedQuestionId));
        if (!visible) throw new IllegalArgumentException("Question is not visible for this response");
        return requireBoundSession(invitation, survey.saveAnswer(invitation.tenantId(),
                normalizedResponseId, normalizedQuestionId, answer));
    }

    public SubmitDistributedSurveyResponseHandler.Outcome submit(String token, String responseId, Instant now) {
        return submissions.handle(token, requiredId(responseId), Objects.requireNonNull(now, "now cannot be null"));
    }

    private SurveyDistribution requireInvitation(String token) {
        return resolver.handle(token).orElseThrow(SubmitDistributedSurveyResponseHandler.InvalidSurveyInvitationException::new);
    }

    private static PublicSurveyAccessPort.ResponseSnapshot requireBoundSession(
            SurveyDistribution invitation, PublicSurveyAccessPort.ResponseSnapshot session) {
        if (!invitation.surveyId().equals(session.surveyId())) {
            throw new SubmitDistributedSurveyResponseHandler.InvalidSurveyInvitationException();
        }
        return session;
    }

    private static String requiredId(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 200)
            throw new IllegalArgumentException("Identifier must contain 1-200 characters");
        return value.trim();
    }
}