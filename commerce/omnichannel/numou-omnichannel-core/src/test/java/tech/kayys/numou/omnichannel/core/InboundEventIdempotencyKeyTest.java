package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import static org.assertj.core.api.Assertions.assertThat;

class InboundEventIdempotencyKeyTest {
    private static final TenantId TENANT_A = TenantId.of("00000000-0000-0000-0000-000000000001");
    private static final TenantId TENANT_B = TenantId.of("00000000-0000-0000-0000-000000000002");

    @Test
    void keyIsStableForSameScopedEvent() {
        InboundEventKey first = new InboundEventKey(TENANT_A, "provider", "account", "event-1");
        InboundEventKey replay = new InboundEventKey(TENANT_A, "provider", "account", "event-1");

        assertThat(InboundEventIdempotencyKey.from(first))
                .isEqualTo(InboundEventIdempotencyKey.from(replay))
                .startsWith("numou-inbound-v1-");
    }

    @Test
    void tenantProviderAccountAndEventIdentityAllContributeToKey() {
        InboundEventKey base = new InboundEventKey(TENANT_A, "provider", "account", "event-1");

        assertThat(InboundEventIdempotencyKey.from(new InboundEventKey(TENANT_B, "provider", "account", "event-1")))
                .isNotEqualTo(InboundEventIdempotencyKey.from(base));
        assertThat(InboundEventIdempotencyKey.from(new InboundEventKey(TENANT_A, "other-provider", "account", "event-1")))
                .isNotEqualTo(InboundEventIdempotencyKey.from(base));
        assertThat(InboundEventIdempotencyKey.from(new InboundEventKey(TENANT_A, "provider", "other-account", "event-1")))
                .isNotEqualTo(InboundEventIdempotencyKey.from(base));
        assertThat(InboundEventIdempotencyKey.from(new InboundEventKey(TENANT_A, "provider", "account", "event-2")))
                .isNotEqualTo(InboundEventIdempotencyKey.from(base));
    }

    @Test
    void keyDoesNotExposeRawExternalIdentity() {
        String key = InboundEventIdempotencyKey.from(
                new InboundEventKey(TENANT_A, "private-provider", "private-account", "private-event"));

        assertThat(key).doesNotContain("private-provider", "private-account", "private-event");
        assertThat(key).hasSize("numou-inbound-v1-".length() + 64);
    }
}
