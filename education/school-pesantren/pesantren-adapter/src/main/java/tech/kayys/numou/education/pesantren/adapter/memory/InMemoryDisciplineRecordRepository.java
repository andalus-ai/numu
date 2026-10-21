package tech.kayys.numou.education.pesantren.adapter.memory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import tech.kayys.numou.education.pesantren.domain.DisciplineRecord;
import tech.kayys.numou.education.pesantren.domain.DisciplineRecordId;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.education.pesantren.spi.DisciplineRecordRepository;

public class InMemoryDisciplineRecordRepository implements DisciplineRecordRepository {
    private final Map<DisciplineRecordId, DisciplineRecord> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<DisciplineRecord> save(DisciplineRecord e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<DisciplineRecord>> findById(DisciplineRecordId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(DisciplineRecordId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(DisciplineRecord e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(DisciplineRecordId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<List<DisciplineRecord>> findBySantri(SantriId santriId) {
        return CompletableFuture.completedFuture(store.values().stream().filter(r -> r.getSantriId().equals(santriId)).collect(Collectors.toList()));
    }
}
