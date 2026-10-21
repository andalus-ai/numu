package tech.kayys.numou.education.lms.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.lms.domain.Quiz;
import tech.kayys.numou.education.lms.domain.QuizId;
import tech.kayys.numou.education.lms.spi.QuizRepository;

public class InMemoryQuizRepository implements QuizRepository {
    private final Map<QuizId, Quiz> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<Quiz> save(Quiz e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<Quiz>> findById(QuizId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(QuizId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(Quiz e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(QuizId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
