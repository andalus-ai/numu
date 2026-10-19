package tech.kayys.numou.survey.domain.response;

import tech.kayys.numou.survey.domain.DomainException;
import tech.kayys.numou.survey.domain.ErrorCode;
import tech.kayys.numou.survey.domain.answer.AnswerValue;
import tech.kayys.numou.survey.domain.event.DomainEvent;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.id.ResponseId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.domain.quiz.ScoreResult;

import java.time.Instant;
import java.util.*;

/** One respondent's (attempt at a) survey. Answers are saved incrementally and locked on submit. */
public final class Response {
    private final ResponseId id;
    private final TenantId tenantId;
    private final SurveyId surveyId;
    private final String respondentId; // null = anonymous
    private final int attempt;
    private final Instant startedAt;
    private ResponseStatus status = ResponseStatus.IN_PROGRESS;
    private final Map<QuestionId, AnswerValue> answers = new LinkedHashMap<>();
    private Instant submittedAt;
    private ScoreResult score;
    private long persistedVersion;
    private final List<DomainEvent> events = new ArrayList<>();

    private Response(ResponseId id, TenantId tenantId, SurveyId surveyId, String respondentId, int attempt, Instant startedAt) {
        this.id = id; this.tenantId = tenantId; this.surveyId = surveyId;
        this.respondentId = respondentId; this.attempt = attempt; this.startedAt = startedAt;
    }

    private Response(Response o) {
        this(o.id, o.tenantId, o.surveyId, o.respondentId, o.attempt, o.startedAt);
        this.status = o.status; this.answers.putAll(o.answers); this.submittedAt = o.submittedAt;
        this.score = o.score; this.persistedVersion = o.persistedVersion;
    }

    public static Response start(ResponseId id, TenantId tenantId, SurveyId surveyId, String respondentId, int attempt, Instant now) {
        return new Response(id, tenantId, surveyId, respondentId, attempt, now);
    }

    public Response copy() { return new Response(this); }

    public void record(QuestionId q, AnswerValue a) {
        assertOpen();
        answers.put(q, a);
    }

    public void clear(QuestionId q) {
        assertOpen();
        answers.remove(q);
    }

    /** Drops answers to questions that are not (or no longer) visible. */
    public void retainOnly(Set<QuestionId> visible) {
        assertOpen();
        answers.keySet().retainAll(visible);
    }

    /** @param scoreOrNull grading outcome for quizzes, null for plain surveys */
    public void submit(ScoreResult scoreOrNull, Instant now) {
        assertOpen();
        this.status = ResponseStatus.SUBMITTED;
        this.submittedAt = now;
        this.score = scoreOrNull;
        events.add(new DomainEvent.ResponseSubmitted(tenantId, surveyId, id,
                scoreOrNull == null ? null : scoreOrNull.percent(), now));
    }

    private void assertOpen() {
        if (status != ResponseStatus.IN_PROGRESS)
            throw new DomainException(ErrorCode.INVALID_STATE, "Response already submitted");
    }

    public ResponseId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public SurveyId surveyId() { return surveyId; }
    public Optional<String> respondentId() { return Optional.ofNullable(respondentId); }
    public int attempt() { return attempt; }
    public Instant startedAt() { return startedAt; }
    public ResponseStatus status() { return status; }
    public Map<QuestionId, AnswerValue> answers() { return Collections.unmodifiableMap(answers); }
    public Optional<Instant> submittedAt() { return Optional.ofNullable(submittedAt); }
    public Optional<ScoreResult> score() { return Optional.ofNullable(score); }
    public long persistedVersion() { return persistedVersion; }
    public void acknowledgePersisted(long v) { this.persistedVersion = v; }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }
}
