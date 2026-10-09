package tech.kayys.syirkah.survey.application.service;

import tech.kayys.syirkah.survey.application.TenantContext;
import tech.kayys.syirkah.survey.application.port.in.ResponseUseCase;
import tech.kayys.syirkah.survey.application.port.out.EventPublisher;
import tech.kayys.syirkah.survey.application.port.out.IdGenerator;
import tech.kayys.syirkah.survey.application.port.out.ResponseRepository;
import tech.kayys.syirkah.survey.application.port.out.SurveyRepository;
import tech.kayys.syirkah.survey.application.port.out.TenantRepository;
import tech.kayys.syirkah.survey.domain.DomainException;
import tech.kayys.syirkah.survey.domain.ErrorCode;
import tech.kayys.syirkah.survey.domain.answer.AnswerValue;
import tech.kayys.syirkah.survey.domain.id.QuestionId;
import tech.kayys.syirkah.survey.domain.id.ResponseId;
import tech.kayys.syirkah.survey.domain.question.Question;
import tech.kayys.syirkah.survey.domain.quiz.QuizSettings;
import tech.kayys.syirkah.survey.domain.quiz.ResultsVisibility;
import tech.kayys.syirkah.survey.domain.quiz.ScoreResult;
import tech.kayys.syirkah.survey.domain.response.Response;
import tech.kayys.syirkah.survey.domain.response.ResponseStatus;
import tech.kayys.syirkah.survey.domain.service.ResponseEvaluator;
import tech.kayys.syirkah.survey.domain.survey.Survey;
import tech.kayys.syirkah.survey.domain.survey.SurveyKind;
import tech.kayys.syirkah.survey.domain.survey.SurveyStatus;
import tech.kayys.syirkah.survey.domain.tenant.Tenant;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class ResponseService implements ResponseUseCase {
    private final SurveyRepository surveys;
    private final ResponseRepository responses;
    private final EventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;
    private final TenantGate gate;
    private final ResponseEvaluator evaluator = new ResponseEvaluator();

    public ResponseService(SurveyRepository surveys, ResponseRepository responses, TenantRepository tenants,
                           EventPublisher events, IdGenerator ids, Clock clock) {
        this.surveys = surveys;
        this.responses = responses;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
        this.gate = new TenantGate(tenants, surveys);
    }

    @Override
    public ResponseSession start(TenantContext ctx, StartResponse cmd) {
        Tenant tenant = gate.activeTenant(ctx.tenantId());
        Survey survey = gate.survey(ctx.tenantId(), cmd.survey());
        Instant now = clock.instant();
        if (!survey.isAcceptingResponses(now))
            throw new DomainException(ErrorCode.INVALID_STATE, "Survey is not accepting responses");

        long submitted = responses.countSubmitted(ctx.tenantId(), survey.id());
        tenant.ensureResponseQuota(submitted);
        int cap = survey.settings().maxResponses();
        if (cap > 0 && submitted >= cap)
            throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "This survey reached its response limit");

        String respondent = cmd.respondentId() == null || cmd.respondentId().isBlank() ? null : cmd.respondentId().trim();
        if (respondent == null && !survey.settings().anonymous())
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "respondentId is required for non-anonymous surveys");

        long attempts = respondent == null ? 0 : responses.countByRespondent(ctx.tenantId(), survey.id(), respondent);
        if (survey.kind() == SurveyKind.QUIZ) {
            int maxAttempts = survey.quizSettings().map(QuizSettings::maxAttempts).orElse(0);
            if (maxAttempts > 0 && attempts >= maxAttempts)
                throw new DomainException(ErrorCode.LIMIT_EXCEEDED, "No attempts left (max " + maxAttempts + ")");
        } else if (!survey.settings().allowMultipleResponses() && attempts > 0) {
            throw new DomainException(ErrorCode.CONFLICT, "This respondent already has a response");
        }

        Response r = Response.start(new ResponseId(ids.next()), ctx.tenantId(), survey.id(), respondent, (int) attempts + 1, now);
        responses.save(r);
        return session(survey, r);
    }

    @Override
    public ResponseSession saveAnswer(TenantContext ctx, SaveAnswer cmd) {
        gate.activeTenant(ctx.tenantId());
        Response r = loadResponse(ctx, cmd.response());
        Survey survey = gate.survey(ctx.tenantId(), r.surveyId());
        requireOpen(r);
        requireWithinTime(survey, r);

        Question q = survey.findQuestion(cmd.question()).orElseThrow(() -> DomainException.notFound("question", cmd.question()));
        boolean visible = evaluator.visibleQuestions(survey, r.answers()).stream().anyMatch(v -> v.id().equals(q.id()));
        if (!visible)
            throw new DomainException(ErrorCode.INVALID_STATE, "Question is not visible for the current answers: " + q.id());

        if (cmd.answer() == null || AnswerValue.isEmpty(cmd.answer())) {
            r.clear(q.id());
        } else {
            List<String> violations = q.type().validate(cmd.answer());
            if (!violations.isEmpty())
                throw new DomainException(ErrorCode.VALIDATION_FAILED, "Invalid answer for " + q.id(), violations);
            r.record(q.id(), cmd.answer());
        }
        responses.save(r);
        return session(survey, r);
    }

    @Override
    public ResponseSession getSession(TenantContext ctx, ResponseId id) {
        gate.activeTenant(ctx.tenantId());
        Response r = loadResponse(ctx, id);
        return session(gate.survey(ctx.tenantId(), r.surveyId()), r);
    }

    @Override
    public SubmissionResult submit(TenantContext ctx, ResponseId id) {
        gate.activeTenant(ctx.tenantId());
        Response r = loadResponse(ctx, id);
        Survey survey = gate.survey(ctx.tenantId(), r.surveyId());
        requireOpen(r);
        requireWithinTime(survey, r);

        List<String> problems = evaluator.submissionProblems(survey, r);
        if (!problems.isEmpty())
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Response cannot be submitted", problems);

        Set<QuestionId> visible = evaluator.visibleQuestions(survey, r.answers()).stream()
                .map(Question::id).collect(Collectors.toSet());
        r.retainOnly(visible);
        ScoreResult score = evaluator.grade(survey, r).orElse(null);
        r.submit(score, clock.instant());
        responses.save(r);
        events.publish(r.pullEvents());

        return new SubmissionResult(r.id(), visibleScore(survey, score), survey.settings().thankYouMessage());
    }

    /** Applies the quiz "results visibility" policy to what the respondent is allowed to see right now. */
    private Optional<ScoreResult> visibleScore(Survey survey, ScoreResult score) {
        if (score == null) return Optional.empty();
        QuizSettings quiz = survey.quizSettings().orElseGet(QuizSettings::defaults);
        boolean show = switch (quiz.resultsVisibility()) {
            case IMMEDIATE -> true;
            case AFTER_CLOSE -> survey.status() == SurveyStatus.CLOSED;
            case NEVER -> false;
        };
        if (!show) return Optional.empty();
        return Optional.of(quiz.showCorrectAnswers() ? score : score.withoutDetails());
    }

    private ResponseSession session(Survey survey, Response r) {
        return new ResponseSession(r.id(), r.surveyId(), r.status(), r.attempt(),
                evaluator.visibleQuestions(survey, r.answers()), r.answers(), evaluator.deadline(survey, r));
    }

    private Response loadResponse(TenantContext ctx, ResponseId id) {
        return responses.findById(ctx.tenantId(), id).orElseThrow(() -> DomainException.notFound("response", id));
    }

    private void requireOpen(Response r) {
        if (r.status() != ResponseStatus.IN_PROGRESS)
            throw new DomainException(ErrorCode.INVALID_STATE, "Response already submitted");
    }

    private void requireWithinTime(Survey survey, Response r) {
        if (evaluator.isExpired(survey, r, clock.instant()))
            throw new DomainException(ErrorCode.TIME_LIMIT_EXCEEDED, "The time limit for this quiz has passed");
    }
}
