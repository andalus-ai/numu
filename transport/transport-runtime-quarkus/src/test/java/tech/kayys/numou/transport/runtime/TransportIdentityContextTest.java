package tech.kayys.numou.transport.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransportIdentityContextTest {
    @Test
    void resolvesTenantAndActorOnlyFromTrustedIdentityAttributes() {
        TransportIdentityContext context = TransportIdentityContext.from(
                "principal-123", "tenant-456", "actor-789");

        assertThat(context.tenantId()).isEqualTo("tenant-456");
        assertThat(context.actorId()).isEqualTo("actor-789");
    }

    @Test
    void rejectsMissingTenantClaim() {
        assertThatThrownBy(() -> TransportIdentityContext.from("principal-123", null, "actor-789"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("tenant claim");
    }

    @Test
    void rejectsMissingActorClaim() {
        assertThatThrownBy(() -> TransportIdentityContext.from("principal-123", "tenant-456", null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("actor claim");
    }

    @Test
    void rejectsBlankOrOversizedTenantContext() {
        assertThatThrownBy(() -> TransportIdentityContext.from("principal-123", "   ", "actor-789"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> TransportIdentityContext.from("principal-123", "x".repeat(129), "actor-789"))
                .isInstanceOf(SecurityException.class);
    }
}
