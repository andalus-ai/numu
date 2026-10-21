package tech.kayys.numou.education.lms.domain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.event.QuizAttempted;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizDomainTest {

    private static final TenantId TENANT = TenantId.generate();

    private static Quiz publishedQuiz() {
        Quiz q = Quiz.create(QuizId.generate(), TENANT, CourseId.generate(), "Kuis 1", 2);
        q.addQuestion(new QuizQuestion("1+1?", List.of("1", "2", "3"), 1, 10));
        q.addQuestion(new QuizQuestion("2+2?", List.of("3", "4"), 1, 20));
        q.publish();
        return q;
    }

    @Test
    void question_validatesShape() {
        assertThatThrownBy(() -> new QuizQuestion("q", List.of("only"), 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuizQuestion("q", List.of("a", "b"), 2, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuizQuestion("q", List.of("a", "b"), 0, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void quiz_cannotPublishEmpty_andFreezesAfterPublish() {
        Quiz q = Quiz.create(QuizId.generate(), TENANT, CourseId.generate(), "K", 1);
        assertThatThrownBy(q::publish).hasMessageContaining("at least one question");
        Quiz published = publishedQuiz();
        assertThatThrownBy(() -> published.addQuestion(new QuizQuestion("x", List.of("a", "b"), 0, 1))).hasMessageContaining("cannot change");
    }

    @Test
    void attempt_autoGradesByPoints() {
        Quiz q = publishedQuiz();
        QuizAttempt a = QuizAttempt.take(QuizAttemptId.generate(), TENANT, q, StudentId.generate(), Map.of(0, 1, 1, 0), Instant.now());
        assertThat(a.getEarnedPoints()).isEqualTo(10);
        assertThat(a.getTotalPoints()).isEqualTo(30);
        assertThat(a.getScorePercent().toPlainString()).isEqualTo("33.33");
        assertThat(a.pullDomainEvents()).hasSize(1).first().isInstanceOf(QuizAttempted.class);
    }

    @Test
    void attempt_unansweredAndOutOfRangeEarnNothing() {
        Quiz q = publishedQuiz();
        QuizAttempt a = QuizAttempt.take(QuizAttemptId.generate(), TENANT, q, StudentId.generate(), Map.of(1, 9), Instant.now());
        assertThat(a.getEarnedPoints()).isEqualTo(0);
        QuizAttempt perfect = QuizAttempt.take(QuizAttemptId.generate(), TENANT, q, StudentId.generate(), Map.of(0, 1, 1, 1), Instant.now());
        assertThat(perfect.getScorePercent().toPlainString()).isEqualTo("100.00");
    }

    @Test
    void attempt_requiresPublishedQuiz() {
        Quiz draft = Quiz.create(QuizId.generate(), TENANT, CourseId.generate(), "K", 1);
        assertThatThrownBy(() -> QuizAttempt.take(QuizAttemptId.generate(), TENANT, draft, StudentId.generate(), Map.of(), Instant.now()))
                .hasMessageContaining("not published");
    }
}
