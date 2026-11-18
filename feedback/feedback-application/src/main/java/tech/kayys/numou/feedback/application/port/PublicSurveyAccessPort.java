package tech.kayys.numou.feedback.application.port;

import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Boundary to the Survey capability for an invitation-authorized respondent.
 * The Feedback application owns invitation authorization; Survey owns response
 * validation, visibility, persistence and scoring.
 */
public interface PublicSurveyAccessPort {
    SurveyDescriptor describe(TenantId tenantId, String surveyId);

    ResponseSnapshot start(TenantId tenantId, String surveyId, String respondentId);

    ResponseSnapshot getSession(TenantId tenantId, String responseId);

    ResponseSnapshot saveAnswer(TenantId tenantId, String responseId,
                                String questionId, Map<String, Object> answer);

    record SurveyDescriptor(String surveyId, String title, String description,
                            String status, boolean anonymous, String thankYouMessage,
                            int questionCount) { }

    record QuestionSnapshot(String id, String title, String description,
                             boolean required, String kind, Object definition) { }

    record ResponseSnapshot(String responseId, String surveyId, String status, int attempt,
                            List<QuestionSnapshot> questions, Map<String, Object> answers,
                            Instant deadline) {
        public ResponseSnapshot {
            questions = List.copyOf(questions == null ? List.of() : questions);
            answers = Map.copyOf(answers == null ? Map.of() : answers);
        }
    }
}