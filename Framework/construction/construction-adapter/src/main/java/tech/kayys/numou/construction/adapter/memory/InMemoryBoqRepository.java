package tech.kayys.syirkah.construction.adapter.memory;

import tech.kayys.syirkah.construction.domain.boq.Boq;
import tech.kayys.syirkah.construction.domain.boq.BoqId;
import tech.kayys.syirkah.construction.spi.boq.BoqRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryBoqRepository implements BoqRepository {
    private final Map<BoqId, Boq> store = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Boq> save(Boq boq) {
        store.put(boq.id(), boq);
        return CompletableFuture.completedFuture(boq);
    }

    @Override
    public CompletionStage<Optional<Boq>> findById(BoqId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Optional<Boq>> findByProjectId(UUID projectId) {
        return CompletableFuture.completedFuture(
                store.values().stream().filter(b -> b.projectId().equals(projectId)).findFirst()
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(BoqId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Boq boq) {
        store.remove(boq.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(BoqId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
