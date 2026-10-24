package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class InboundEventEnvelopeTest {

    private static final ChannelContext CONTEXT = ChannelContext.of(
            TenantId.of("00000000-0000-0000-0000-000000000001"),
            "branch-jkt", ChannelType.MARKETPLACE, "provider-account-1", "correlation-1");
    private static final String HASH = "a".repeat(64);

    @Test
    void trimsExternalMetadataAndNormalizesDigest() {
        InboundEventEnvelope envelope = new InboundEventEnvelope(
                CONTEXT, " event-123 ", " order.created ", HASH.toUpperCase(),
                Instant.parse("2026-01-01T00:00:00Z"), Optional.empty());

        assertThat(envelope.externalEventId()).isEqualTo("event-123");
        assertThat(envelope.eventType()).isEqualTo("order.created");
        assertThat(envelope.payloadSha256()).isEqualTo(HASH);
    }

    @Test
    void rejectsMalformedDigest() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new InboundEventEnvelope(CONTEXT, "event-1", "order.created", "not-a-digest",
                        Instant.now(), Optional.empty()));
    }

    @Test
    void rejectsOversizedExternalIdentifier() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new InboundEventEnvelope(CONTEXT, "x".repeat(251), "order.created", HASH,
                        Instant.now(), Optional.empty()));
    }
}
