package tech.kayys.numou.education.school.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.school.domain.Teacher;
import tech.kayys.numou.education.school.spi.TeacherRepository;

public class InMemoryTeacherRepository implements TeacherRepository {
    private final Map<TeacherId, Teacher> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<Teacher> save(Teacher e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<Teacher>> findById(TeacherId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(TeacherId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(Teacher e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(TeacherId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
