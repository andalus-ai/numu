package tech.kayys.numou.survey.application.port.out;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.domain.tenant.Tenant;

import java.util.List;
import java.util.Optional;

/** Persistence port for survey tenants. */
public interface TenantRepository {
    Optional<Tenant> findById(TenantId tenantId);

    List<Tenant> findAll();

    void save(Tenant tenant);
}
