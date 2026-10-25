package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookIngressLimitsTest {
    @Test
    void acceptsBodyExactlyAtLimit() throws IOException {
        WebhookIngressLimits limits = new WebhookIngressLimits(4);
        limits.validateDeclaredContentLength(4);

        assertThat(new String(limits.readBounded(
                new ByteArrayInputStream("1234".getBytes(StandardCharsets.UTF_8))),
                StandardCharsets.UTF_8)).isEqualTo("1234");
    }

    @Test
    void rejectsDeclaredOversizeBeforeReading() {
        WebhookIngressLimits limits = new WebhookIngressLimits(4);
        AtomicInteger reads = new AtomicInteger();
        InputStream input = new InputStream() {
            @Override public int read() {
                reads.incrementAndGet();
                return -1;
            }
        };

        assertThatThrownBy(() -> limits.validateDeclaredContentLength(5))
                .isInstanceOf(WebhookIngressLimits.PayloadTooLargeException.class);
        assertThat(reads).hasValue(0);
    }

    @Test
    void rejectsOversizeStreamEvenWhenLengthIsUnknown() {
        WebhookIngressLimits limits = new WebhookIngressLimits(4);
        assertThatThrownBy(() -> limits.readBounded(
                new ByteArrayInputStream("12345".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(WebhookIngressLimits.PayloadTooLargeException.class)
                .hasMessageContaining("4 bytes");
    }

    @Test
    void stopsAfterReadingOnlyOneProbeByteBeyondLimit() {
        WebhookIngressLimits limits = new WebhookIngressLimits(4);
        AtomicInteger consumed = new AtomicInteger();
        InputStream input = new InputStream() {
            private final byte[] body = "123456789".getBytes(StandardCharsets.UTF_8);
            @Override public int read() {
                if (consumed.get() >= body.length) return -1;
                return body[consumed.getAndIncrement()] & 0xff;
            }
            @Override public int read(byte[] target, int offset, int length) {
                if (consumed.get() >= body.length) return -1;
                int count = Math.min(length, body.length - consumed.get());
                System.arraycopy(body, consumed.get(), target, offset, count);
                consumed.addAndGet(count);
                return count;
            }
        };

        assertThatThrownBy(() -> limits.readBounded(input))
                .isInstanceOf(WebhookIngressLimits.PayloadTooLargeException.class);
        assertThat(consumed).hasValue(5);
    }

    @Test
    void rejectsInvalidDeclaredLengthsAndNonPositiveLimits() {
        assertThatThrownBy(() -> new WebhookIngressLimits(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WebhookIngressLimits(4).validateDeclaredContentLength(-2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
