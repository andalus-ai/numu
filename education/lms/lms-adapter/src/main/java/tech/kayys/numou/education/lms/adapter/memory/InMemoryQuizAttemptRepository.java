package tech.kayys.numou.education.lms.adapter.memory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.lms.domain.QuizAttempt;
import tech.kayys.numou.education.lms.domain.QuizAttemptId;
import tech.kayys.numou.education.lms.domain.QuizId;
import tech.kayys.numou.education.lms.spi.QuizAttemptRepository;

public class InMemoryQuizAttemptRepository implements QuizAttemptRepository {
    private final Map<QuizAttemptId, QuizAttempt> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<QuizAttempt> save(QuizAttempt e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<QuizAttempt>> findById(QuizAttemptId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(QuizAttemptId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(QuizAttempt e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(QuizAttemptId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<List<QuizAttempt>> findByQuizAndStudent(QuizId quizId, StudentId studentId) {
        return CompletableFuture.completedFuture(store.values().stream().filter(a -> a.getQuizId().equals(quizId) && a.getStudentId().equals(studentId)).collect(Collectors.toList()));
    }
}
