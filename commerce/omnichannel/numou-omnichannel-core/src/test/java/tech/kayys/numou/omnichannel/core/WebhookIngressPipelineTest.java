package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookIngressPipelineTest {
    private static final ChannelContext CONTEXT = ChannelContext.of(
            TenantId.of("00000000-0000-0000-0000-000000000001"),
            null, ChannelType.MARKETPLACE, "account-1", "corr-1");

    @Test
    void oversizedDeclaredBodyIsRejectedBeforeReadingFactoryOrIngestion() {
        WebhookIngressPipeline pipeline = new WebhookIngressPipeline(new WebhookIngressLimits(4));
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicInteger ingestionCalls = new AtomicInteger();
        InputStream input = new InputStream() {
            @Override public int read() {
                reads.incrementAndGet();
                return -1;
            }
        };

        assertThatThrownBy(() -> pipeline.ingest(
                Map.of("X-Signature", "sig"), input, 5,
                (headers, body) -> {
                    factoryCalls.incrementAndGet();
                    return request(headers, body);
                },
                request -> {
                    ingestionCalls.incrementAndGet();
                    return rejected();
                }))
                .isInstanceOf(WebhookIngressLimits.PayloadTooLargeException.class);

        assertThat(reads).hasValue(0);
        assertThat(factoryCalls).hasValue(0);
        assertThat(ingestionCalls).hasValue(0);
    }

    @Test
    void malformedHeadersAreRejectedBeforeBodyRead() {
        WebhookIngressPipeline pipeline = new WebhookIngressPipeline(new WebhookIngressLimits(4));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Signature", "one");
        headers.put("x-signature", "two");
        AtomicInteger reads = new AtomicInteger();
        InputStream input = new InputStream() {
            @Override public int read() {
                reads.incrementAndGet();
                return -1;
            }
        };

        assertThatThrownBy(() -> pipeline.ingest(
                headers, input, -1, (h, body) -> request(h, body), ignored -> rejected()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
        assertThat(reads).hasValue(0);
    }

    @Test
    void readsBoundedBodyThenBuildsRequestAndInvokesIngestion() throws IOException {
        WebhookIngressPipeline pipeline = new WebhookIngressPipeline(new WebhookIngressLimits(4));
        AtomicInteger factoryCalls = new AtomicInteger();
        AtomicInteger ingestionCalls = new AtomicInteger();
        byte[] bytes = "data".getBytes(StandardCharsets.UTF_8);

        InboundIngestionResult result = pipeline.ingest(
                Map.of("X-Signature", "sig"),
                new ByteArrayInputStream(bytes),
                bytes.length,
                (headers, body) -> {
                    factoryCalls.incrementAndGet();
                    assertThat(body).containsExactly(bytes);
                    return request(headers, body);
                },
                request -> {
                    ingestionCalls.incrementAndGet();
                    assertThat(request.rawBody()).containsExactly(bytes);
                    return rejected();
                });

        assertThat(result.disposition()).isEqualTo(InboundIngestionDisposition.REJECTED);
        assertThat(factoryCalls).hasValue(1);
        assertThat(ingestionCalls).hasValue(1);
    }

    @Test
    void rejectsFactoryThatChangesTheBoundedBodyBeforeIngestion() {
        WebhookIngressPipeline pipeline = new WebhookIngressPipeline(new WebhookIngressLimits(4));
        AtomicInteger ingestionCalls = new AtomicInteger();

        assertThatThrownBy(() -> pipeline.ingest(
                Map.of("X-Signature", "sig"),
                new ByteArrayInputStream("data".getBytes(StandardCharsets.UTF_8)),
                4,
                (headers, body) -> request(headers, "evil".getBytes(StandardCharsets.UTF_8)),
                request -> {
                    ingestionCalls.incrementAndGet();
                    return rejected();
                }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bounded request body");
        assertThat(ingestionCalls).hasValue(0);
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
