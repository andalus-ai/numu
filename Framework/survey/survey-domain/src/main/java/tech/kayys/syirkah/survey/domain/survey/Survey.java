package tech.kayys.syirkah.survey.domain.survey;

import tech.kayys.syirkah.survey.domain.DomainException;
import tech.kayys.syirkah.survey.domain.ErrorCode;
import tech.kayys.syirkah.survey.domain.Guard;
import tech.kayys.syirkah.survey.domain.event.DomainEvent;
import tech.kayys.syirkah.survey.domain.id.QuestionId;
import tech.kayys.syirkah.survey.domain.id.SectionId;
import tech.kayys.syirkah.survey.domain.id.SurveyId;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.survey.domain.question.Question;
import tech.kayys.syirkah.survey.domain.quiz.QuizSettings;

import java.time.Instant;
import java.util.*;

/**
 * Survey aggregate root: sections + questions + settings, with a DRAFT -> PUBLISHED -> CLOSED -> ARCHIVED
 * lifecycle. Only DRAFT surveys are editable; to change a published survey call {@link #revise}, which
 * produces a new draft version (existing responses keep pointing at the version they were collected on).
 */
public final class Survey {
    private final SurveyId id;
    private final TenantId tenantId;
    private final SurveyKind kind;
    private String title;
    private String description;
    private SurveyStatus status;
    private final List<Section> sections = new ArrayList<>();
    private SurveySettings settings;
    private QuizSettings quiz; // only for QUIZ
    private final int version;
    private final SurveyId previousVersion;
    private final Instant createdAt;
    private Instant updatedAt;
    private long persistedVersion; // optimistic-locking token maintained by the persistence adapter
    private final List<DomainEvent> events = new ArrayList<>();

    private Survey(SurveyId id, TenantId tenantId, SurveyKind kind, String title, String description, int version,
                   SurveyId previousVersion, Instant now) {
        this.id = Guard.notNull(id, "survey id");
        this.tenantId = Guard.notNull(tenantId, "tenant id");
        this.kind = Guard.notNull(kind, "kind");
        this.title = Guard.notBlank(title, "survey title");
        this.description = description == null ? "" : description;
        this.status = SurveyStatus.DRAFT;
        this.settings = SurveySettings.defaults();
        this.quiz = kind == SurveyKind.QUIZ ? QuizSettings.defaults() : null;
        this.version = version;
        this.previousVersion = previousVersion;
        this.createdAt = now;
        this.updatedAt = now;
    }

    private Survey(Survey o) {
        this.id = o.id; this.tenantId = o.tenantId; this.kind = o.kind; this.title = o.title;
        this.description = o.description; this.status = o.status; this.sections.addAll(o.sections);
        this.settings = o.settings; this.quiz = o.quiz; this.version = o.version;
        this.previousVersion = o.previousVersion; this.createdAt = o.createdAt; this.updatedAt = o.updatedAt;
        this.persistedVersion = o.persistedVersion;
    }

    public static Survey draft(SurveyId id, TenantId tenantId, String title, String description, SurveyKind kind, Instant now) {
        return new Survey(id, tenantId, kind, title, description, 1, null, now);
    }

    /** Deep-enough copy (sections/questions are immutable) used by persistence adapters to avoid aliasing. */
    public Survey copy() { return new Survey(this); }

    // ------------------------------------------------------------------ authoring

    public void updateDetails(String title, String description, Instant now) {
        assertEditable();
        this.title = Guard.notBlank(title, "survey title");
        this.description = description == null ? "" : description;
        touch(now);
    }

    public void updateSettings(SurveySettings s, Instant now) {
        assertEditable();
        this.settings = Guard.notNull(s, "settings");
        touch(now);
    }

    public void configureQuiz(QuizSettings q, Instant now) {
        assertEditable();
        if (kind != SurveyKind.QUIZ) throw new DomainException(ErrorCode.INVALID_STATE, "Only QUIZ surveys have quiz settings");
        this.quiz = Guard.notNull(q, "quiz settings");
        touch(now);
    }

    public Section addSection(SectionId sectionId, String title, String description, Instant now) {
        assertEditable();
        if (sections.stream().anyMatch(s -> s.id().equals(sectionId)))
            throw new DomainException(ErrorCode.CONFLICT, "Section already exists: " + sectionId);
        Section s = new Section(sectionId, title, description, List.of());
        sections.add(s);
        touch(now);
        return s;
    }

    public void addQuestion(SectionId sectionId, Question q, Instant now) {
        assertEditable();
        if (findQuestion(q.id()).isPresent())
            throw new DomainException(ErrorCode.CONFLICT, "Question id already used in this survey: " + q.id());
        int si = sectionIndex(sectionId);
        Section s = sections.get(si);
        List<Question> qs = new ArrayList<>(s.questions());
        qs.add(q);
        sections.set(si, s.withQuestions(qs));
        touch(now);
    }

    public void replaceQuestion(Question q, Instant now) {
        assertEditable();
        int[] loc = locate(q.id());
        Section s = sections.get(loc[0]);
        List<Question> qs = new ArrayList<>(s.questions());
        qs.set(loc[1], q);
        sections.set(loc[0], s.withQuestions(qs));
        touch(now);
    }

    public void removeQuestion(QuestionId qid, Instant now) {
        assertEditable();
        for (Question other : questions()) {
            if (!other.id().equals(qid) && other.visibleIf().references().contains(qid))
                throw new DomainException(ErrorCode.INVALID_STATE,
                        "Question " + qid + " is referenced by the logic of " + other.id());
        }
        int[] loc = locate(qid);
        Section s = sections.get(loc[0]);
        List<Question> qs = new ArrayList<>(s.questions());
        qs.remove(loc[1]);
        sections.set(loc[0], s.withQuestions(qs));
        touch(now);
    }

    public void moveQuestion(QuestionId qid, SectionId target, int index, Instant now) {
        assertEditable();
        int ti = sectionIndex(target);
        int[] loc = locate(qid);
        Section src = sections.get(loc[0]);
        List<Question> sq = new ArrayList<>(src.questions());
        Question q = sq.remove(loc[1]);
        sections.set(loc[0], src.withQuestions(sq));
        Section tgt = sections.get(ti);
        List<Question> tq = new ArrayList<>(tgt.questions());
        tq.add(Math.max(0, Math.min(index, tq.size())), q);
        sections.set(ti, tgt.withQuestions(tq));
        touch(now);
    }

    // ------------------------------------------------------------------ lifecycle

    /** Everything that currently prevents publishing; empty = ready. */
    public List<String> publishProblems() {
        List<String> p = new ArrayList<>();
        List<Question> qs = questions();
        if (qs.isEmpty()) p.add("NO_QUESTIONS");
        Set<QuestionId> seen = new HashSet<>();
        for (Question q : qs) {
            q.problems().forEach(s -> p.add(q.id() + ":" + s));
            for (QuestionId ref : q.visibleIf().references()) {
                if (!seen.contains(ref)) p.add(q.id() + ":CONDITION_REFERENCES_UNKNOWN_OR_LATER_QUESTION(" + ref + ")");
            }
            seen.add(q.id());
        }
        boolean anyScored = qs.stream().anyMatch(q -> q.scoring().isScored());
        if (kind == SurveyKind.QUIZ && !anyScored) p.add("QUIZ_HAS_NO_SCORED_QUESTIONS");
        if (kind == SurveyKind.SURVEY && anyScored) p.add("SCORING_ONLY_ALLOWED_ON_QUIZ");
        if (settings.opensAt() != null && settings.closesAt() != null && !settings.closesAt().isAfter(settings.opensAt()))
            p.add("INVALID_SCHEDULE");
        return p;
    }

    public void publish(Instant now) {
        require(SurveyStatus.DRAFT, "publish");
        List<String> problems = publishProblems();
        if (!problems.isEmpty())
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Survey is not ready to publish", problems);
        status = SurveyStatus.PUBLISHED;
        touch(now);
        events.add(new DomainEvent.SurveyPublished(tenantId, id, version, now));
    }

    public void close(Instant now) {
        require(SurveyStatus.PUBLISHED, "close");
        status = SurveyStatus.CLOSED;
        touch(now);
        events.add(new DomainEvent.SurveyClosed(tenantId, id, now));
    }

    public void archive(Instant now) {
        if (status != SurveyStatus.CLOSED && status != SurveyStatus.DRAFT)
            throw new DomainException(ErrorCode.INVALID_STATE, "Only DRAFT or CLOSED surveys can be archived");
        status = SurveyStatus.ARCHIVED;
        touch(now);
    }

    /** New editable draft (version + 1) based on a published or closed survey. */
    public Survey revise(SurveyId newId, Instant now) {
        if (status != SurveyStatus.PUBLISHED && status != SurveyStatus.CLOSED)
            throw new DomainException(ErrorCode.INVALID_STATE, "Only PUBLISHED or CLOSED surveys can be revised");
        Survey next = new Survey(newId, tenantId, kind, title, description, version + 1, id, now);
        next.sections.addAll(sections);
        next.settings = settings;
        next.quiz = quiz;
        return next;
    }

    public boolean isAcceptingResponses(Instant now) {
        return status == SurveyStatus.PUBLISHED
                && (settings.opensAt() == null || !now.isBefore(settings.opensAt()))
                && (settings.closesAt() == null || now.isBefore(settings.closesAt()));
    }

    // ------------------------------------------------------------------ queries

    public List<Question> questions() {
        List<Question> all = new ArrayList<>();
        sections.forEach(s -> all.addAll(s.questions()));
        return Collections.unmodifiableList(all);
    }

    public int questionCount() { return questions().size(); }

    public Optional<Question> findQuestion(QuestionId qid) {
        return questions().stream().filter(q -> q.id().equals(qid)).findFirst();
    }

    public SurveyId id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public SurveyKind kind() { return kind; }
    public String title() { return title; }
    public String description() { return description; }
    public SurveyStatus status() { return status; }
    public List<Section> sections() { return Collections.unmodifiableList(sections); }
    public SurveySettings settings() { return settings; }
    public Optional<QuizSettings> quizSettings() { return Optional.ofNullable(quiz); }
    public int version() { return version; }
    public Optional<SurveyId> previousVersion() { return Optional.ofNullable(previousVersion); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long persistedVersion() { return persistedVersion; }

    /** Called by persistence adapters after a successful save. */
    public void acknowledgePersisted(long newVersion) { this.persistedVersion = newVersion; }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> out = List.copyOf(events);
        events.clear();
        return out;
    }

    // ------------------------------------------------------------------ internals

    private void assertEditable() {
        if (status != SurveyStatus.DRAFT)
            throw new DomainException(ErrorCode.INVALID_STATE,
                    "Survey is " + status + "; only DRAFT surveys can be edited (use revise to create a new version)");
    }

    private void require(SurveyStatus expected, String action) {
        if (status != expected)
            throw new DomainException(ErrorCode.INVALID_STATE, "Cannot " + action + " a survey that is " + status);
    }

    private void touch(Instant now) { this.updatedAt = now; }

    private int sectionIndex(SectionId sid) {
        for (int i = 0; i < sections.size(); i++) if (sections.get(i).id().equals(sid)) return i;
        throw DomainException.notFound("section", sid);
    }

    private int[] locate(QuestionId qid) {
        for (int i = 0; i < sections.size(); i++) {
            List<Question> qs = sections.get(i).questions();
            for (int j = 0; j < qs.size(); j++) if (qs.get(j).id().equals(qid)) return new int[]{i, j};
        }
        throw DomainException.notFound("question", qid);
    }
}
