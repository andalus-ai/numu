package tech.kayys.syirkah.tenancy.application.command.tenant;

import tech.kayys.syirkah.tenancy.domain.tenant.Tenant;
import tech.kayys.syirkah.tenancy.spi.port.TenantRepository;

import java.util.Objects;

public final class DeactivateTenantUseCase {

    private final TenantRepository repository;

    public DeactivateTenantUseCase(TenantRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void execute(DeactivateTenantCommand command) {
        Tenant tenant = repository.findById(command.tenantId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant not found: " + command.tenantId()));
        tenant.deactivate();
        repository.save(tenant);
    }
}
