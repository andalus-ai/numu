package tech.kayys.syirkah.tenancy.domain.tenant;

import tech.kayys.syirkah.foundation.domain.tenant.TenantId;

import tech.kayys.syirkah.foundation.domain.audit.AuditMeta;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class TenantTest {

    private static AuditMeta audit() {
        return AuditMeta.initial("system", Instant.now());
    }

    @Test
    void creates_in_provisioning_state() {
        TenantId id = TenantId.newId();
        Tenant tenant = Tenant.create(id, "ACME", "ACME Corp", null, audit());

        assertThat(tenant.status()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(tenant.code()).isEqualTo("ACME");
        assertThat(tenant.name()).isEqualTo("ACME Corp");
        assertThat(tenant.organization()).isNull();
    }

    @Test
    void activate_from_provisioning() {
        Tenant tenant = Tenant.create(TenantId.newId(), "T1", "Tenant 1", null, audit());
        tenant.activate();
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void activate_from_suspended() {
        Tenant tenant = Tenant.create(TenantId.newId(), "T2", "Tenant 2", null, audit());
        tenant.activate();
        tenant.suspend();
        tenant.activate();
        assertThat(tenant.status()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void suspend_requires_active() {
        Tenant tenant = Tenant.create(TenantId.newId(), "T3", "Tenant 3", null, audit());
        assertThatThrownBy(tenant::suspend)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be suspended from PROVISIONING");
    }

    @Test
    void deactivate_is_idempotent() {
        Tenant tenant = Tenant.create(TenantId.newId(), "T4", "Tenant 4", null, audit());
        tenant.activate();
        tenant.deactivate();
        tenant.deactivate(); // should not throw
        assertThat(tenant.status()).isEqualTo(TenantStatus.INACTIVE);
    }

    @Test
    void raises_created_event() {
        Tenant tenant = Tenant.create(TenantId.newId(), "EVT", "Event Tenant", null, audit());
        assertThat(tenant.getDomainEvents()).hasSize(1);
    }

    @Test
    void code_blank_throws() {
        assertThatThrownBy(() -> Tenant.create(TenantId.newId(), "", "Name", null, audit()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
