package tech.kayys.syirkah.organization.adapter.memory;

import tech.kayys.syirkah.organization.domain.OrganizationUnit;
import tech.kayys.syirkah.organization.domain.OrganizationUnitId;
import tech.kayys.syirkah.organization.spi.OrganizationUnitRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOrganizationUnitRepository implements OrganizationUnitRepository {

    private final Map<OrganizationUnitId, OrganizationUnit> store = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<OrganizationUnit> save(OrganizationUnit entity) {
        store.put(entity.getId(), entity);
        return CompletableFuture.completedFuture(entity);
    }

    @Override
    public CompletionStage<Optional<OrganizationUnit>> findById(OrganizationUnitId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(OrganizationUnitId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(OrganizationUnit entity) {
        store.remove(entity.getId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(OrganizationUnitId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
