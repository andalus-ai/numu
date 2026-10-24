package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InboundEventKeyTest {

    private static final String TENANT = "00000000-0000-0000-0000-000000000001";

    private InboundEventEnvelope envelope(String account, String eventId) {
        return new InboundEventEnvelope(
                ChannelContext.of(TenantId.of(TENANT), null, ChannelType.MARKETPLACE, account, "corr-1"),
                eventId, "order.created", "a".repeat(64), Instant.parse("2026-01-01T00:00:00Z"), Optional.empty());
    }

    @Test
    void scopesProviderEventsByTenantProviderAndAccount() {
        InboundEventKey key = InboundEventKey.from(envelope(" account-1 ", " event-1 "), " provider-a ");

        assertThat(key.providerId()).isEqualTo("provider-a");
        assertThat(key.providerAccountId()).isEqualTo("account-1");
        assertThat(key.externalEventId()).isEqualTo("event-1");
        assertThat(key.tenantId()).isEqualTo(TenantId.of(TENANT));
    }

    @Test
    void rejectsBlankProviderIdentity() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                InboundEventKey.from(envelope("account-1", "event-1"), " "));
    }
}
