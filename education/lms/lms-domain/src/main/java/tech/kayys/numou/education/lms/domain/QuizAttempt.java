package tech.kayys.numou.education.lms.domain;

import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.event.QuizAttempted;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One graded attempt at a quiz; immutable once created. */
public final class QuizAttempt extends AbstractAggregateRoot<QuizAttemptId> {

    private final TenantId tenantId;
    private final QuizId quizId;
    private final StudentId studentId;
    private final Map<Integer, Integer> answers;
    private final int earnedPoints;
    private final int totalPoints;
    private final BigDecimal scorePercent;
    private final Instant submittedAt;

    private QuizAttempt(QuizAttemptId id, TenantId tenantId, Quiz quiz, StudentId studentId,
                        Map<Integer, Integer> answers, Instant now) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.quizId = quiz.getId();
        this.studentId = Objects.requireNonNull(studentId, "studentId must not be null");
        this.answers = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(answers, "answers must not be null")));
        this.earnedPoints = quiz.pointsFor(answers);
        this.totalPoints = quiz.totalPoints();
        this.scorePercent = BigDecimal.valueOf(earnedPoints).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalPoints), 2, RoundingMode.HALF_UP);
        this.submittedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public static QuizAttempt take(QuizAttemptId id, TenantId tenantId, Quiz quiz, StudentId studentId,
                                   Map<Integer, Integer> answers, Instant now) {
        if (quiz.getStatus() != QuizStatus.PUBLISHED) throw new IllegalStateException("Quiz is not published");
        QuizAttempt a = new QuizAttempt(id, tenantId, quiz, studentId, answers, now);
        a.raise(new QuizAttempted(id, tenantId, quiz.getId(), studentId, a.scorePercent));
        return a;
    }

    public TenantId getTenantId() { return tenantId; }
    public QuizId getQuizId() { return quizId; }
    public StudentId getStudentId() { return studentId; }
    public Map<Integer, Integer> getAnswers() { return answers; }
    public int getEarnedPoints() { return earnedPoints; }
    public int getTotalPoints() { return totalPoints; }
    public BigDecimal getScorePercent() { return scorePercent; }
    public Instant getSubmittedAt() { return submittedAt; }
}
