package tech.kayys.syirkah.organization.adapter.memory;

import tech.kayys.syirkah.identity.domain.user.UserId;
import tech.kayys.syirkah.organization.domain.OrganizationId;
import tech.kayys.syirkah.organization.domain.OrganizationMembership;
import tech.kayys.syirkah.organization.domain.OrganizationMembershipId;
import tech.kayys.syirkah.organization.spi.OrganizationMembershipRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryOrganizationMembershipRepository implements OrganizationMembershipRepository {

    private final Map<OrganizationMembershipId, OrganizationMembership> store = new ConcurrentHashMap<>();

    @Override
    public synchronized CompletionStage<OrganizationMembership> save(OrganizationMembership membership) {
        boolean duplicate = store.values().stream().anyMatch(existing ->
                !existing.getId().equals(membership.getId())
                        && existing.organizationId().equals(membership.organizationId())
                        && existing.userId().equals(membership.userId()));
        if (duplicate) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("User already has an organization membership"));
        }
        store.put(membership.getId(), membership);
        return CompletableFuture.completedFuture(membership);
    }

    @Override
    public CompletionStage<Optional<OrganizationMembership>> findById(OrganizationMembershipId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(OrganizationMembershipId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(OrganizationMembership membership) {
        store.remove(membership.getId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(OrganizationMembershipId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Optional<OrganizationMembership>> findByOrganizationAndUser(
            OrganizationId organizationId, UserId userId) {
        return CompletableFuture.completedFuture(store.values().stream()
                .filter(membership -> membership.organizationId().equals(organizationId)
                        && membership.userId().equals(userId))
                .findFirst());
    }
}
