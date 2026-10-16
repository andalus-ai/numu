package tech.kayys.syirkah.tenancy.domain.membership;

import tech.kayys.syirkah.foundation.domain.audit.AuditMeta;
import tech.kayys.syirkah.foundation.domain.ref.UserRef;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class TenantMembershipTest {

    private static AuditMeta audit() {
        return AuditMeta.initial("system", Instant.now());
    }

    @Test
    void invite_creates_invited_status() {
        TenantMembership m = TenantMembership.invite(
                TenantMembershipId.newId(),
                TenantId.newId(),
                UserRef.generate(),
                TenantMemberType.MEMBER,
                audit()
        );
        assertThat(m.status()).isEqualTo(TenantMemberStatus.INVITED);
    }

    @Test
    void accept_transitions_to_active() {
        TenantMembership m = TenantMembership.invite(
                TenantMembershipId.newId(), TenantId.newId(),
                UserRef.generate(), TenantMemberType.MEMBER, audit()
        );
        m.accept();
        assertThat(m.status()).isEqualTo(TenantMemberStatus.ACTIVE);
    }

    @Test
    void cannot_accept_twice() {
        TenantMembership m = TenantMembership.invite(
                TenantMembershipId.newId(), TenantId.newId(),
                UserRef.generate(), TenantMemberType.MEMBER, audit()
        );
        m.accept();
        assertThatThrownBy(m::accept)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void remove_is_idempotent() {
        TenantMembership m = TenantMembership.invite(
                TenantMembershipId.newId(), TenantId.newId(),
                UserRef.generate(), TenantMemberType.MEMBER, audit()
        );
        m.remove();
        m.remove(); // should not throw
        assertThat(m.status()).isEqualTo(TenantMemberStatus.REMOVED);
    }
}
