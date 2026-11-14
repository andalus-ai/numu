package tech.kayys.numou.feedback.adapter.survey;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.application.TenantContext;
import tech.kayys.numou.survey.application.port.in.ResponseUseCase;
import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.id.ResponseId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.question.Question;
import tech.kayys.numou.survey.domain.quiz.ScoreResult;
import tech.kayys.numou.survey.domain.response.ResponseStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SurveyResponseSubmissionBridgeTest {
    @Test
    void verifiesSurveyAndSubmitsAnOpenResponse() {
        TenantId tenant = TenantId.generate();
        FakeResponseUseCase responses = new FakeResponseUseCase(session("response-1", "survey-1", ResponseStatus.IN_PROGRESS));
        new SurveyResponseSubmissionBridge(responses).submit(tenant, "survey-1", "response-1");
        assertEquals(1, responses.submissions);
        assertEquals(tenant, responses.lastContext.tenantId());
        assertEquals("public-invitation", responses.lastContext.actorId());
    }

    @Test
    void doesNotSubmitAnAlreadySubmittedResponseAgain() {
        FakeResponseUseCase responses = new FakeResponseUseCase(session("response-1", "survey-1", ResponseStatus.SUBMITTED));
        new SurveyResponseSubmissionBridge(responses).submit(TenantId.generate(), "survey-1", "response-1");
        assertEquals(0, responses.submissions);
    }

    @Test
    void rejectsResponseFromAnotherSurvey() {
        FakeResponseUseCase responses = new FakeResponseUseCase(session("response-1", "survey-other", ResponseStatus.IN_PROGRESS));
        assertThrows(IllegalArgumentException.class,
                () -> new SurveyResponseSubmissionBridge(responses).submit(TenantId.generate(), "survey-1", "response-1"));
        assertEquals(0, responses.submissions);
    }

    private static ResponseUseCase.ResponseSession session(String responseId, String surveyId, ResponseStatus status) {
        return new ResponseUseCase.ResponseSession(new ResponseId(responseId), new SurveyId(surveyId), status,
                1, List.<Question>of(), Map.<QuestionId, AnswerValue>of(), Optional.empty());
    }

    private static final class FakeResponseUseCase implements ResponseUseCase {
        private final ResponseSession session;
        private int submissions;
        private TenantContext lastContext;

        private FakeResponseUseCase(ResponseSession session) { this.session = session; }

        @Override public ResponseSession start(TenantContext ctx, StartResponse cmd) { throw new UnsupportedOperationException(); }
        @Override public ResponseSession saveAnswer(TenantContext ctx, SaveAnswer cmd) { throw new UnsupportedOperationException(); }
        @Override public ResponseSession getSession(TenantContext ctx, ResponseId id) { return session; }
        @Override public SubmissionResult submit(TenantContext ctx, ResponseId id) {
            submissions++;
            lastContext = ctx;
            return new SubmissionResult(id, Optional.<ScoreResult>empty(), "Thanks");
        }
    }
}
