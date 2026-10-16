package tech.kayys.numou.tenancy.spi.port;

import tech.kayys.numou.tenancy.domain.settings.TenantSettings;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Optional;

/** Repository port for TenantSettings (exactly one per Tenant). */
public interface TenantSettingsRepository {

    TenantSettings save(TenantSettings settings);

    Optional<TenantSettings> findByTenantId(TenantId tenantId);

    boolean existsByTenantId(TenantId tenantId);
}
