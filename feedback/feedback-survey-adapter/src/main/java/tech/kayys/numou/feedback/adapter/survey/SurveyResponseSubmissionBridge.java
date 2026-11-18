package tech.kayys.numou.feedback.adapter.survey;

import tech.kayys.numou.feedback.application.port.DistributedSurveyResponsePort;
import tech.kayys.numou.feedback.application.port.PublicSurveyAccessPort;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.application.TenantContext;
import tech.kayys.numou.survey.application.port.in.ResponseUseCase;
import tech.kayys.numou.survey.application.port.in.SurveyAuthoringUseCase;
import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.id.OptionId;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.id.ResponseId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.question.Question;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bridges invitation-authorized Feedback requests to Survey's own application use cases.
 * Survey remains authoritative for tenant checks, response state, answer validation and scoring.
 */
public final class SurveyResponseSubmissionBridge
        implements DistributedSurveyResponsePort, PublicSurveyAccessPort {
    private final ResponseUseCase responses;
    private final SurveyAuthoringUseCase surveys;
    private final Clock clock;

    /** Retained for callers that only need the original submit-only port. */
    public SurveyResponseSubmissionBridge(ResponseUseCase responses) {
        this(responses, null, Clock.systemUTC());
    }

    public SurveyResponseSubmissionBridge(ResponseUseCase responses, SurveyAuthoringUseCase surveys) {
        this(responses, surveys, Clock.systemUTC());
    }

    public SurveyResponseSubmissionBridge(ResponseUseCase responses, SurveyAuthoringUseCase surveys, Clock clock) {
        this.responses = Objects.requireNonNull(responses, "responses cannot be null");
        this.surveys = surveys;
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    @Override
    public SurveyDescriptor describe(TenantId tenantId, String surveyId) {
        if (surveys == null) throw new IllegalStateException("Survey authoring query is not configured");
        var survey = surveys.getSurvey(context(tenantId), new SurveyId(surveyId));
        if (!survey.isAcceptingResponses(clock.instant())) {
            throw new IllegalStateException("Survey is not accepting public responses");
        }
        return new SurveyDescriptor(survey.id().value(), survey.title(), survey.description(),
                survey.status().name(), survey.settings().anonymous(), survey.settings().thankYouMessage(),
                survey.questionCount());
    }

    @Override
    public ResponseSnapshot start(TenantId tenantId, String surveyId, String respondentId) {
        return snapshot(responses.start(context(tenantId),
                new ResponseUseCase.StartResponse(new SurveyId(surveyId), respondentId)));
    }

    @Override
    public ResponseSnapshot getSession(TenantId tenantId, String responseId) {
        return snapshot(responses.getSession(context(tenantId), new ResponseId(responseId)));
    }

    @Override
    public ResponseSnapshot saveAnswer(TenantId tenantId, String responseId,
                                        String questionId, Map<String, Object> answer) {
        return snapshot(responses.saveAnswer(context(tenantId),
                new ResponseUseCase.SaveAnswer(new ResponseId(responseId),
                        new QuestionId(questionId), parseAnswer(answer))));
    }

    @Override
    public void submit(TenantId tenantId, String surveyId, String responseId) {
        TenantContext context = context(tenantId);
        ResponseId id = new ResponseId(responseId);
        ResponseUseCase.ResponseSession session = responses.getSession(context, id);
        if (!surveyId.equals(session.surveyId().value())) {
            throw new IllegalArgumentException("Response does not belong to the invited survey");
        }
        // A retry may arrive after Survey committed but before Feedback finalized its quota claim.
        if (session.status() == tech.kayys.numou.survey.domain.response.ResponseStatus.SUBMITTED) return;
        responses.submit(context, id);
    }

    private static TenantContext context(TenantId tenantId) {
        return new TenantContext(Objects.requireNonNull(tenantId, "tenantId cannot be null"),
                "public-invitation");
    }

    private static ResponseSnapshot snapshot(ResponseUseCase.ResponseSession session) {
        Map<String, Object> answers = new LinkedHashMap<>();
        session.answers().forEach((id, answer) -> answers.put(id.value(), answer));
        return new ResponseSnapshot(session.id().value(), session.surveyId().value(),
                session.status().name(), session.attempt(),
                session.visibleQuestions().stream().map(SurveyResponseSubmissionBridge::question).toList(),
                answers, session.deadline().orElse(null));
    }

    private static QuestionSnapshot question(Question question) {
        return new QuestionSnapshot(question.id().value(), question.title(), question.description(),
                question.required(), question.type().kind(), question.type());
    }

    private static AnswerValue parseAnswer(Map<String, Object> value) {
        if (value == null || value.isEmpty()) return null;
        if (value.containsKey("text")) return new AnswerValue.Text(asString(value.get("text"), "text"));
        if (value.containsKey("number")) return new AnswerValue.Numeric(asNumber(value.get("number"), "number"));
        if (value.containsKey("date")) {
            try {
                return new AnswerValue.Day(LocalDate.parse(asString(value.get("date"), "date")));
            } catch (java.time.DateTimeException invalidDate) {
                throw new IllegalArgumentException("date must use ISO-8601 yyyy-MM-dd format");
            }
        }
        if (value.containsKey("bool")) {
            if (!(value.get("bool") instanceof Boolean bool)) throw new IllegalArgumentException("bool must be a boolean");
            return new AnswerValue.Bool(bool);
        }
        if (value.containsKey("choice") || value.containsKey("other")) {
            Set<OptionId> selected = new LinkedHashSet<>();
            Object raw = value.get("choice");
            if (raw instanceof List<?> list) list.forEach(item -> selected.add(new OptionId(asString(item, "choice item"))));
            else if (raw != null) throw new IllegalArgumentException("choice must be an array of option IDs");
            return new AnswerValue.Choice(selected,
                    value.get("other") == null ? "" : asString(value.get("other"), "other"));
        }
        if (value.containsKey("ranking")) {
            Object raw = value.get("ranking");
            if (!(raw instanceof List<?> list)) throw new IllegalArgumentException("ranking must be an array");
            return new AnswerValue.Ranking(list.stream()
                    .map(item -> new OptionId(asString(item, "ranking item"))).toList());
        }
        if (value.containsKey("matrix")) {
            Object raw = value.get("matrix");
            if (!(raw instanceof Map<?, ?> rows)) throw new IllegalArgumentException("matrix must be an object");
            Map<OptionId, Set<OptionId>> cells = new LinkedHashMap<>();
            rows.forEach((row, columns) -> {
                if (!(columns instanceof List<?> list)) throw new IllegalArgumentException("matrix row must be an array");
                Set<OptionId> selected = list.stream()
                        .map(item -> new OptionId(asString(item, "matrix column"))).collect(Collectors.toCollection(LinkedHashSet::new));
                cells.put(new OptionId(asString(row, "matrix row")), selected);
            });
            return new AnswerValue.Matrix(cells);
        }
        throw new IllegalArgumentException("Answer must contain text, number, date, bool, choice, ranking or matrix");
    }

    private static String asString(Object value, String field) {
        if (!(value instanceof String text) || text.length() > 20_000)
            throw new IllegalArgumentException(field + " must be a string of at most 20000 characters");
        return text;
    }

    private static double asNumber(Object value, String field) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
            throw new IllegalArgumentException(field + " must be a finite number");
        return number.doubleValue();
    }
}
