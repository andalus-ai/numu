package tech.kayys.syirkah.tenancy.domain.provisioning;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TenantProvisioningTest {

    @Test
    void starts_in_not_started_state() {
        TenantProvisioning p = TenantProvisioning.start(
                TenantProvisioningId.newId(), TenantId.newId());
        assertThat(p.status()).isEqualTo(ProvisioningStatus.NOT_STARTED);
    }

    @Test
    void begin_transitions_to_in_progress() {
        TenantProvisioning p = TenantProvisioning.start(
                TenantProvisioningId.newId(), TenantId.newId());
        p.begin();
        assertThat(p.status()).isEqualTo(ProvisioningStatus.IN_PROGRESS);
    }

    @Test
    void complete_step_tracking() {
        TenantProvisioning p = TenantProvisioning.start(
                TenantProvisioningId.newId(), TenantId.newId());
        p.begin();
        assertThat(p.isCompleted(ProvisioningStep.TENANT_SETTINGS)).isFalse();
        p.completeStep(ProvisioningStep.TENANT_SETTINGS);
        assertThat(p.isCompleted(ProvisioningStep.TENANT_SETTINGS)).isTrue();
    }

    @Test
    void complete_sets_completed_status() {
        TenantProvisioning p = TenantProvisioning.start(
                TenantProvisioningId.newId(), TenantId.newId());
        p.begin();
        p.complete();
        assertThat(p.status()).isEqualTo(ProvisioningStatus.COMPLETED);
    }
}
