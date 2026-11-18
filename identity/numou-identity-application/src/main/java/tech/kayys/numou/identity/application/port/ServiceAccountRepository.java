package tech.kayys.numou.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.serviceaccount.ServiceAccount;
import tech.kayys.numou.identity.domain.serviceaccount.ServiceAccountId;

import java.util.List;
import java.util.Optional;

/**
 * Tenant-scoped persistence contract for machine identities.
 * Every operation requires the tenant selected by the authenticated boundary.
 */
public interface ServiceAccountRepository {

    Uni<Optional<ServiceAccount>> findById(TenantId tenantId, ServiceAccountId id);

    Uni<Optional<ServiceAccount>> findByName(TenantId tenantId, String name);

    Uni<Boolean> existsByName(TenantId tenantId, String name);

    Uni<List<ServiceAccount>> findAll(TenantId tenantId);

    Uni<Void> save(TenantId tenantId, ServiceAccount serviceAccount);
}
