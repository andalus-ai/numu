package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookIngressFramingTest {
    private static final ChannelContext CONTEXT = ChannelContext.of(
            TenantId.of("00000000-0000-0000-0000-000000000001"),
            null, ChannelType.MARKETPLACE, "account-1", "corr-1");

    @Test
    void rejectsBodyShorterThanDeclaredBeforeRequestConstructionOrIngestion() {
        var pipeline = new WebhookIngressPipeline(new WebhookIngressLimits(16));
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicInteger ingestionCalls = new AtomicInteger();

        assertThatThrownBy(() -> pipeline.ingest(
                Map.of("X-Signature", "sig"),
                bytes("abc"),
                4,
                (headers, body) -> {
                    factoryCalls.incrementAndGet();
                    return request(headers, body);
                },
                request -> {
                    ingestionCalls.incrementAndGet();
                    return rejected();
                }))
                .isInstanceOf(WebhookIngressPipeline.ContentLengthMismatchException.class)
                .satisfies(error -> {
                    var mismatch = (WebhookIngressPipeline.ContentLengthMismatchException) error;
                    assertThat(mismatch.declaredLength()).isEqualTo(4);
                    assertThat(mismatch.actualLength()).isEqualTo(3);
                });

        assertThat(factoryCalls).hasValue(0);
        assertThat(ingestionCalls).hasValue(0);
    }

    @Test
    void rejectsBodyLongerThanDeclaredBeforeRequestConstructionOrIngestion() {
        var pipeline = new WebhookIngressPipeline(new WebhookIngressLimits(16));
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicInteger ingestionCalls = new AtomicInteger();

        assertThatThrownBy(() -> pipeline.ingest(
                Map.of("X-Signature", "sig"),
                bytes("abc"),
                2,
                (headers, body) -> {
                    factoryCalls.incrementAndGet();
                    return request(headers, body);
                },
                request -> {
                    ingestionCalls.incrementAndGet();
                    return rejected();
                }))
                .isInstanceOf(WebhookIngressPipeline.ContentLengthMismatchException.class);

        assertThat(factoryCalls).hasValue(0);
        assertThat(ingestionCalls).hasValue(0);
    }

    @Test
    void acceptsExactDeclaredLengthAndUnknownLength() throws Exception {
        var pipeline = new WebhookIngressPipeline(new WebhookIngressLimits(16));
        byte[] body = "abc".getBytes(StandardCharsets.UTF_8);

        var exact = pipeline.ingest(Map.of("X-Signature", "sig"), new ByteArrayInputStream(body),
                3, WebhookIngressFramingTest::request, ignored -> rejected());
        var unknown = pipeline.ingest(Map.of("X-Signature", "sig"), new ByteArrayInputStream(body),
                -1, WebhookIngressFramingTest::request, ignored -> rejected());

        assertThat(exact.disposition()).isEqualTo(InboundIngestionDisposition.REJECTED);
        assertThat(unknown.disposition()).isEqualTo(InboundIngestionDisposition.REJECTED);
    }

    @Test
    void validatesCallbacksBeforeConsumingBody() {
        var pipeline = new WebhookIngressPipeline(new WebhookIngressLimits(16));
        AtomicInteger reads = new AtomicInteger();
        InputStream input = new InputStream() {
            @Override public int read() {
                reads.incrementAndGet();
                return -1;
            }
        };

        assertThatThrownBy(() -> pipeline.ingest(Map.of(), input, -1, null, ignored -> rejected()))
                .isInstanceOf(NullPointerException.class);
        assertThat(reads).hasValue(0);
    }

    private static ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static InboundWebhookRequest request(Map<String, String> headers, byte[] body) {
        return new InboundWebhookRequest(
                CONTEXT, "test-provider", headers, body, "event-1", "test.event", Optional.of(Instant.EPOCH));
    }

    private static InboundIngestionResult rejected() {
        return new InboundIngestionResult(
                InboundIngestionDisposition.REJECTED, "TEST_RESULT", Optional.empty());
    }
}
