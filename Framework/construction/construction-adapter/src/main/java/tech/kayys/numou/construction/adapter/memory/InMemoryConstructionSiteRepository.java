package tech.kayys.syirkah.construction.adapter.memory;

import tech.kayys.syirkah.construction.domain.site.ConstructionSite;
import tech.kayys.syirkah.construction.domain.site.ConstructionSiteId;
import tech.kayys.syirkah.construction.spi.site.ConstructionSiteRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryConstructionSiteRepository implements ConstructionSiteRepository {
    private final Map<ConstructionSiteId, ConstructionSite> store = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ConstructionSite> save(ConstructionSite site) {
        store.put(site.id(), site);
        return CompletableFuture.completedFuture(site);
    }

    @Override
    public CompletionStage<Optional<ConstructionSite>> findById(ConstructionSiteId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<List<ConstructionSite>> findByProjectId(UUID projectId) {
        return CompletableFuture.completedFuture(
                store.values().stream().filter(s -> s.projectId().equals(projectId)).toList()
        );
    }

    @Override
    public CompletionStage<Boolean> existsById(ConstructionSiteId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(ConstructionSite site) {
        store.remove(site.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ConstructionSiteId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
