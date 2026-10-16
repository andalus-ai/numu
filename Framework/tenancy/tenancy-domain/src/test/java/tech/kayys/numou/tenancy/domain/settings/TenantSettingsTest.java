package tech.kayys.syirkah.tenancy.domain.settings;

import tech.kayys.syirkah.foundation.domain.audit.AuditMeta;
import tech.kayys.syirkah.foundation.domain.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class TenantSettingsTest {

    private static AuditMeta audit() {
        return AuditMeta.initial("system", Instant.now());
    }

    @Test
    void defaults_are_utc_en() {
        TenantSettings s = TenantSettings.defaults(TenantId.newId(), audit());
        assertThat(s.timezone().value()).isEqualTo("UTC");
        assertThat(s.locale().value()).isEqualTo("en");
    }

    @Test
    void timezone_cannot_be_blank() {
        assertThatThrownBy(() -> TimeZoneId.of(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void change_timezone() {
        TenantSettings s = TenantSettings.defaults(TenantId.newId(), audit());
        s.changeTimezone(TimeZoneId.of("Asia/Jakarta"));
        assertThat(s.timezone().value()).isEqualTo("Asia/Jakarta");
    }
}
