package tech.kayys.syirkah.tenancy.adapter.memory;

import tech.kayys.syirkah.foundation.domain.ref.UserRef;
import tech.kayys.syirkah.tenancy.domain.membership.TenantMembership;
import tech.kayys.syirkah.tenancy.domain.membership.TenantMembershipId;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.tenancy.spi.port.TenantMembershipRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryTenantMembershipRepository implements TenantMembershipRepository {

    private final Map<TenantMembershipId, TenantMembership> store = new ConcurrentHashMap<>();

    @Override
    public TenantMembership save(TenantMembership membership) {
        store.put(membership.id(), membership);
        return membership;
    }

    @Override
    public Optional<TenantMembership> findById(TenantId tenantId, TenantMembershipId membershipId) {
        return Optional.ofNullable(store.get(membershipId))
                .filter(m -> m.tenantId().equals(tenantId));
    }

    @Override
    public Optional<TenantMembership> findByUser(TenantId tenantId, UserRef user) {
        return store.values().stream()
                .filter(m -> m.tenantId().equals(tenantId) && m.user().equals(user))
                .findFirst();
    }

    @Override
    public boolean exists(TenantId tenantId, UserRef user) {
        return store.values().stream()
                .anyMatch(m -> m.tenantId().equals(tenantId) && m.user().equals(user));
    }
}
