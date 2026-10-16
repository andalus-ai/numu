package tech.kayys.syirkah.organization.adapter.memory;

import tech.kayys.syirkah.organization.domain.Organization;
import tech.kayys.syirkah.organization.domain.OrganizationId;
import tech.kayys.syirkah.organization.spi.OrganizationRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOrganizationRepository implements OrganizationRepository {

    private final Map<OrganizationId, Organization> store = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Organization> save(Organization entity) {
        store.put(entity.getId(), entity);
        return CompletableFuture.completedFuture(entity);
    }

    @Override
    public CompletionStage<Optional<Organization>> findById(OrganizationId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(OrganizationId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Organization entity) {
        store.remove(entity.getId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(OrganizationId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
