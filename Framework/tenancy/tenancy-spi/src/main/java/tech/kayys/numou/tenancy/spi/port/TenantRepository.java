package tech.kayys.syirkah.tenancy.spi.port;

import tech.kayys.syirkah.tenancy.domain.tenant.Tenant;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.util.Optional;

/**
 * Repository port for the Tenant aggregate.
 * Implementation is infrastructure (JPA, Mongo, in-memory) — never imported by domain.
 */
public interface TenantRepository {

    Tenant save(Tenant tenant);

    Optional<Tenant> findById(TenantId tenantId);

    Optional<Tenant> findByCode(String code);

    boolean existsByCode(String code);
}
