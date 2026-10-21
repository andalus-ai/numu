package tech.kayys.numou.education.school.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.school.domain.School;
import tech.kayys.numou.education.school.spi.SchoolRepository;

public class InMemorySchoolRepository implements SchoolRepository {
    private final Map<SchoolId, School> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<School> save(School e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<School>> findById(SchoolId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(SchoolId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(School e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(SchoolId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
