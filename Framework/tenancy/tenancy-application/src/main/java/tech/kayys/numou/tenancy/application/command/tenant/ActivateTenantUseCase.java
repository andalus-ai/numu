package tech.kayys.syirkah.tenancy.application.command.tenant;

import tech.kayys.syirkah.tenancy.domain.tenant.Tenant;
import tech.kayys.syirkah.tenancy.spi.port.TenantRepository;

import java.util.Objects;

public final class ActivateTenantUseCase {

    private final TenantRepository repository;

    public ActivateTenantUseCase(TenantRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void execute(ActivateTenantCommand command) {
        Tenant tenant = repository.findById(command.tenantId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant not found: " + command.tenantId()));
        tenant.activate();
        repository.save(tenant);
    }
}
