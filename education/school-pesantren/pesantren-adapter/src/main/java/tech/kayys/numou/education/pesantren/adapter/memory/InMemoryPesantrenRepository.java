package tech.kayys.numou.education.pesantren.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.pesantren.domain.Pesantren;
import tech.kayys.numou.education.pesantren.domain.PesantrenId;
import tech.kayys.numou.education.pesantren.spi.PesantrenRepository;

public class InMemoryPesantrenRepository implements PesantrenRepository {
    private final Map<PesantrenId, Pesantren> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<Pesantren> save(Pesantren e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<Pesantren>> findById(PesantrenId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(PesantrenId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(Pesantren e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(PesantrenId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
