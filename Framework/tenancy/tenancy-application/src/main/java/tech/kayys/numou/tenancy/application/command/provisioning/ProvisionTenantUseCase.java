package tech.kayys.syirkah.tenancy.application.command.provisioning;

import tech.kayys.syirkah.tenancy.domain.provisioning.ProvisioningStatus;
import tech.kayys.syirkah.tenancy.domain.provisioning.ProvisioningStep;
import tech.kayys.syirkah.tenancy.domain.provisioning.TenantProvisioning;
import tech.kayys.syirkah.tenancy.domain.provisioning.TenantProvisioningId;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import tech.kayys.syirkah.tenancy.spi.port.TenantAccessProvisioner;
import tech.kayys.syirkah.tenancy.spi.port.TenantFeatureProvisioner;
import tech.kayys.syirkah.tenancy.spi.port.TenantProvisioningRepository;
import tech.kayys.syirkah.tenancy.spi.port.TenantRepository;
import tech.kayys.syirkah.tenancy.spi.port.TenantSettingsProvisioner;
import tech.kayys.syirkah.tenancy.spi.port.TenantStorageProvisioner;
import tech.kayys.syirkah.tenancy.spi.port.TenantSubscriptionProvisioner;

import java.util.Objects;

/**
 * Orchestrates the full tenant provisioning workflow.
 *
 * <p>Each step is idempotent and checkpointed — safe to retry after partial failure.
 * Follows the spec's T-06 design: no giant method; individual provisioners per step.
 */
public final class ProvisionTenantUseCase {

    private final TenantRepository tenantRepository;
    private final TenantProvisioningRepository provisioningRepository;
    private final TenantSettingsProvisioner settingsProvisioner;
    private final TenantFeatureProvisioner featureProvisioner;
    private final TenantSubscriptionProvisioner subscriptionProvisioner;
    private final TenantAccessProvisioner accessProvisioner;
    private final TenantStorageProvisioner storageProvisioner;

    public ProvisionTenantUseCase(
            TenantRepository tenantRepository,
            TenantProvisioningRepository provisioningRepository,
            TenantSettingsProvisioner settingsProvisioner,
            TenantFeatureProvisioner featureProvisioner,
            TenantSubscriptionProvisioner subscriptionProvisioner,
            TenantAccessProvisioner accessProvisioner,
            TenantStorageProvisioner storageProvisioner) {
        this.tenantRepository       = Objects.requireNonNull(tenantRepository);
        this.provisioningRepository = Objects.requireNonNull(provisioningRepository);
        this.settingsProvisioner    = Objects.requireNonNull(settingsProvisioner);
        this.featureProvisioner     = Objects.requireNonNull(featureProvisioner);
        this.subscriptionProvisioner= Objects.requireNonNull(subscriptionProvisioner);
        this.accessProvisioner      = Objects.requireNonNull(accessProvisioner);
        this.storageProvisioner     = Objects.requireNonNull(storageProvisioner);
    }

    public void execute(TenantId tenantId) {
        tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        TenantProvisioning provisioning = provisioningRepository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    TenantProvisioning created = TenantProvisioning.start(
                            TenantProvisioningId.newId(), tenantId);
                    provisioningRepository.save(created);
                    return created;
                });

        if (provisioning.status() == ProvisioningStatus.COMPLETED) {
            return;
        }

        provisioning.begin();

        executeStep(provisioning, ProvisioningStep.TENANT_SETTINGS,
                () -> settingsProvisioner.provision(tenantId));
        executeStep(provisioning, ProvisioningStep.FEATURE_CONFIGURATION,
                () -> featureProvisioner.provision(tenantId));
        executeStep(provisioning, ProvisioningStep.SUBSCRIPTION,
                () -> subscriptionProvisioner.provision(tenantId));
        executeStep(provisioning, ProvisioningStep.ACCESS,
                () -> accessProvisioner.provision(tenantId));
        executeStep(provisioning, ProvisioningStep.STORAGE,
                () -> storageProvisioner.provision(tenantId));

        provisioning.complete();
        provisioningRepository.save(provisioning);
    }

    private void executeStep(TenantProvisioning provisioning,
                              ProvisioningStep step,
                              Runnable action) {
        if (provisioning.isCompleted(step)) {
            return; // already done — skip on retry
        }
        action.run();
        provisioning.completeStep(step);
        provisioningRepository.save(provisioning);
    }
}
