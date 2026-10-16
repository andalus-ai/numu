package tech.kayys.syirkah.identity.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.identity.domain.role.Role;
import tech.kayys.syirkah.identity.domain.role.RoleId;

import java.util.Optional;

public interface RoleRepository {
    Uni<Optional<Role>> findById(TenantId tenantId, RoleId roleId);

    Uni<Optional<Role>> findByCode(TenantId tenantId, String code);

    Uni<Void> save(Role role);
}
