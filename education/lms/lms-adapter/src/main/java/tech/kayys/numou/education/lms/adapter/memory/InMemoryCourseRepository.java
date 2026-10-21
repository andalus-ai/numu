package tech.kayys.numou.education.lms.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.lms.domain.Course;
import tech.kayys.numou.education.lms.domain.CourseId;
import tech.kayys.numou.education.lms.spi.CourseRepository;

public class InMemoryCourseRepository implements CourseRepository {
    private final Map<CourseId, Course> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<Course> save(Course e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<Course>> findById(CourseId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(CourseId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(Course e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(CourseId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
