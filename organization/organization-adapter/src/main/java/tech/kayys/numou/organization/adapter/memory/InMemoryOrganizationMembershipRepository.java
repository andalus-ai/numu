package tech.kayys.numou.organization.adapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import tech.kayys.numou.organization.domain.OrganizationId;
import tech.kayys.numou.organization.domain.OrganizationMembership;
import tech.kayys.numou.organization.domain.OrganizationMembershipId;
import tech.kayys.numou.organization.spi.OrganizationMembershipRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe in-memory adapter with explicit tenant ownership for each membership. */
public class InMemoryOrganizationMembershipRepository implements OrganizationMembershipRepository {

    private record Entry(TenantId tenantId, OrganizationMembership membership) { }
    private final Map<OrganizationMembershipId, Entry> store = new ConcurrentHashMap<>();

    /** Legacy unscoped persistence is rejected; tenant ownership is mandatory. */
    @Override
    @Deprecated(forRemoval = false)
    public synchronized CompletionStage<OrganizationMembership> save(OrganizationMembership membership) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "TenantId is required to persist an organization membership"));
    }

    @Override
    public synchronized CompletionStage<OrganizationMembership> save(
            OrganizationMembership membership, TenantId tenantId) {
        Objects.requireNonNull(membership, "membership");
        Objects.requireNonNull(tenantId, "tenantId");
        Entry byId = store.get(membership.getId());
        if (byId != null && !tenantId.equals(byId.tenantId())) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Membership ID already exists in another tenant"));
        }
        boolean duplicate = store.values().stream().anyMatch(entry ->
                tenantId.equals(entry.tenantId())
                        && !entry.membership().getId().equals(membership.getId())
                        && entry.membership().organizationId().equals(membership.organizationId())
                        && entry.membership().userId().equals(membership.userId()));
        if (duplicate) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("User already has an organization membership in this tenant"));
        }
        store.put(membership.getId(), new Entry(tenantId, membership));
        return CompletableFuture.completedFuture(membership);
    }

    @Override
    public CompletionStage<Optional<OrganizationMembership>> findById(OrganizationMembershipId id) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "TenantId is required for membership reads; use findByTenantAndId"));
    }

    @Override
    public CompletionStage<Boolean> existsByTenantAndId(TenantId tenantId, OrganizationMembershipId membershipId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(membershipId, "membershipId");
        Entry entry = store.get(membershipId);
        return CompletableFuture.completedFuture(entry != null && tenantId.equals(entry.tenantId()));
    }

    @Override
    public CompletionStage<Optional<OrganizationMembership>> findByTenantAndId(
            TenantId tenantId, OrganizationMembershipId membershipId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(membershipId, "membershipId");
        Entry entry = store.get(membershipId);
        return CompletableFuture.completedFuture(entry != null && tenantId.equals(entry.tenantId())
                ? Optional.of(entry.membership()) : Optional.empty());
    }

    @Override
    public CompletionStage<Boolean> existsById(OrganizationMembershipId id) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "TenantId is required for membership existence checks; use existsByTenantAndId"));
    }

    @Override
    public CompletionStage<Void> delete(OrganizationMembership membership) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "Membership history is retained; use the tenant-scoped lifecycle transition"));
    }

    @Override
    public CompletionStage<Void> deleteById(OrganizationMembershipId id) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "Membership history is retained; use the tenant-scoped lifecycle transition"));
    }

    @Override
    @Deprecated(forRemoval = false)
    public CompletionStage<Optional<OrganizationMembership>> findByOrganizationAndUser(
            OrganizationId organizationId, UserId userId) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "TenantId is required for membership reads; use findByTenantAndOrganizationAndUser"));
    }

    @Override
    public CompletionStage<Optional<OrganizationMembership>> findByTenantAndOrganizationAndUser(
            TenantId tenantId, OrganizationId organizationId, UserId userId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(userId, "userId");
        return CompletableFuture.completedFuture(store.values().stream()
                .filter(entry -> tenantId.equals(entry.tenantId())
                        && entry.membership().organizationId().equals(organizationId)
                        && entry.membership().userId().equals(userId))
                .map(Entry::membership).findFirst());
    }
}
