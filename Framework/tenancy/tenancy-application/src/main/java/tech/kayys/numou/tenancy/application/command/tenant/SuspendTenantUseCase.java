package tech.kayys.numou.tenancy.application.command.tenant;

import tech.kayys.numou.tenancy.domain.tenant.Tenant;
import tech.kayys.numou.tenancy.spi.port.TenantRepository;

import java.util.Objects;

public final class SuspendTenantUseCase {

    private final TenantRepository repository;

    public SuspendTenantUseCase(TenantRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void execute(SuspendTenantCommand command) {
        Tenant tenant = repository.findById(command.tenantId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant not found: " + command.tenantId()));
        tenant.suspend();
        repository.save(tenant);
    }
}
