package tech.kayys.syirkah.tenancy.application.command.settings;

import tech.kayys.syirkah.tenancy.domain.settings.TenantSettings;
import tech.kayys.syirkah.tenancy.spi.port.TenantSettingsRepository;

import java.util.Objects;

public final class ChangeTenantTimezoneUseCase {

    private final TenantSettingsRepository repository;

    public ChangeTenantTimezoneUseCase(TenantSettingsRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public void execute(ChangeTenantTimezoneCommand command) {
        TenantSettings settings = repository.findByTenantId(command.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Tenant settings not found"));
        settings.changeTimezone(command.timezone());
        repository.save(settings);
    }
}
