package tech.kayys.syirkah.tenancy.spi.port;

import tech.kayys.syirkah.tenancy.domain.settings.TenantSettings;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import java.util.Optional;

/** Repository port for TenantSettings (exactly one per Tenant). */
public interface TenantSettingsRepository {

    TenantSettings save(TenantSettings settings);

    Optional<TenantSettings> findByTenantId(TenantId tenantId);

    boolean existsByTenantId(TenantId tenantId);
}
