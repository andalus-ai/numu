package tech.kayys.syirkah.tenancy.application.command.tenant;

import tech.kayys.syirkah.foundation.domain.audit.AuditMeta;
import tech.kayys.syirkah.tenancy.domain.tenant.Tenant;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.tenancy.spi.port.TenantRepository;

import java.time.Instant;
import java.util.Objects;

/** Creates a new Tenant and persists it in PROVISIONING state. */
public final class CreateTenantUseCase {

    private final TenantRepository repository;

    public CreateTenantUseCase(TenantRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public TenantId execute(CreateTenantCommand command) {
        if (repository.existsByCode(command.code())) {
            throw new IllegalStateException("Tenant code already exists: " + command.code());
        }

        TenantId id     = TenantId.newId();
        Instant  now    = Instant.now();
        AuditMeta audit = AuditMeta.initial(command.actor(), now);

        Tenant tenant = Tenant.create(id, command.code(), command.name(),
                command.organization(), audit);

        repository.save(tenant);
        return id;
    }
}
