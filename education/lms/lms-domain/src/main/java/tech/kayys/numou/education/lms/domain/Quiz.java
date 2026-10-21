package tech.kayys.numou.education.lms.domain;

import tech.kayys.numou.education.lms.domain.event.QuizPublished;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** An auto-graded multiple-choice quiz attached to a course. Questions are frozen once published. */
public final class Quiz extends AbstractAggregateRoot<QuizId> {

    private final TenantId tenantId;
    private final CourseId courseId;
    private final String title;
    private final int maxAttempts;
    private final List<QuizQuestion> questions = new ArrayList<>();
    private QuizStatus status = QuizStatus.DRAFT;

    private Quiz(QuizId id, TenantId tenantId, CourseId courseId, String title, int maxAttempts) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.courseId = Objects.requireNonNull(courseId, "courseId must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Quiz title must not be blank");
        this.title = title.trim();
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts must be positive");
        this.maxAttempts = maxAttempts;
    }

    public static Quiz create(QuizId id, TenantId tenantId, CourseId courseId, String title, int maxAttempts) {
        return new Quiz(id, tenantId, courseId, title, maxAttempts);
    }

    public void addQuestion(QuizQuestion q) {
        if (status != QuizStatus.DRAFT) throw new IllegalStateException("Published quizzes cannot change");
        questions.add(Objects.requireNonNull(q, "question must not be null"));
        touch();
    }

    public void publish() {
        if (status != QuizStatus.DRAFT) throw new IllegalStateException("Quiz is already published");
        if (questions.isEmpty()) throw new IllegalStateException("A quiz needs at least one question before publishing");
        status = QuizStatus.PUBLISHED;
        touch();
        raise(new QuizPublished(getId(), tenantId, courseId));
    }

    public int totalPoints() { return questions.stream().mapToInt(QuizQuestion::points).sum(); }

    /**
     * Points earned for the given answers (question index to chosen option index).
     * Unanswered or out-of-range answers earn nothing.
     */
    public int pointsFor(Map<Integer, Integer> answers) {
        int earned = 0;
        for (int i = 0; i < questions.size(); i++) {
            Integer chosen = answers.get(i);
            if (chosen != null && chosen == questions.get(i).correctIndex()) earned += questions.get(i).points();
        }
        return earned;
    }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public CourseId getCourseId() { return courseId; }
    public String getTitle() { return title; }
    public int getMaxAttempts() { return maxAttempts; }
    public QuizStatus getStatus() { return status; }
    public List<QuizQuestion> getQuestions() { return Collections.unmodifiableList(questions); }
}
