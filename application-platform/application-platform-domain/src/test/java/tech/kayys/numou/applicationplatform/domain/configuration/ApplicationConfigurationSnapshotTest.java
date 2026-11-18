package tech.kayys.numou.applicationplatform.domain.configuration;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.applicationplatform.domain.installation.ApplicationId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.*;

class ApplicationConfigurationSnapshotTest {
    private static final Instant NOW = Instant.parse("2026-11-04T03:00:00Z");

    @Test
    void createsAndAdvancesImmutableTenantScopedRevisions() {
        var tenant = TenantId.generate();
        var application = new ApplicationId("com.example.crm");
        var first = ApplicationConfigurationSnapshot.first(tenant, application, "1.0",
                "{\"locale\":\"id-ID\"}", "user:alice", NOW);
        var second = first.next("1.1", "{\"locale\":\"en-US\"}", "user:bob", NOW.plusSeconds(10));

        assertThat(first.revision()).isEqualTo(1);
        assertThat(second.revision()).isEqualTo(2);
        assertThat(second.tenantId()).isEqualTo(tenant);
        assertThat(second.applicationId()).isEqualTo(application);
        assertThat(first.payloadJson()).isEqualTo("{\"locale\":\"id-ID\"}");
        assertThat(second.payloadJson()).isEqualTo("{\"locale\":\"en-US\"}");
        assertThat(second.payloadSha256()).isNotEqualTo(first.payloadSha256());
    }

    @Test
    void rejectsUnsupportedSchemaVersionsMalformedPayloadDigestsAndInvalidMetadata() {
        var tenant = TenantId.generate();
        var app = new ApplicationId("com.example.crm");
        assertThatThrownBy(() -> ApplicationConfigurationSnapshot.first(tenant, app, "v1",
                "{}", "actor", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ApplicationConfigurationSnapshot(tenant, app, "1.0", 1,
                "{}", "0".repeat(64), "actor", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationConfigurationSnapshot.first(tenant, app, "1.0",
                " ", "actor", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ApplicationConfigurationSnapshot.first(tenant, app, "1.0",
                "{}", " ", NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsRevisionOverflow() {
        var tenant = TenantId.generate();
        var app = new ApplicationId("com.example.crm");
        var snapshot = new ApplicationConfigurationSnapshot(tenant, app, "1.0", Long.MAX_VALUE,
                "{}", sha256("{}"), "actor", NOW);
        assertThatThrownBy(() -> snapshot.next("1.0", "{}", "actor", NOW))
                .isInstanceOf(ArithmeticException.class);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
