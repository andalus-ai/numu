package tech.kayys.syirkah.tenancy.spi.port;

import tech.kayys.syirkah.foundation.domain.ref.UserRef;
import tech.kayys.syirkah.tenancy.domain.membership.TenantMembership;
import tech.kayys.syirkah.tenancy.domain.membership.TenantMembershipId;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.util.Optional;

/** Repository port for the TenantMembership aggregate. */
public interface TenantMembershipRepository {

    TenantMembership save(TenantMembership membership);

    /** Lookup by (tenantId, membershipId) — tenant-scoped for safety. */
    Optional<TenantMembership> findById(TenantId tenantId, TenantMembershipId membershipId);

    Optional<TenantMembership> findByUser(TenantId tenantId, UserRef user);

    boolean exists(TenantId tenantId, UserRef user);
}
