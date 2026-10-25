package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookHeadersTest {
    @Test
    void acceptsValidHeadersAndReturnsAnImmutableCopy() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("X-Signature", "abc123");
        Map<String, String> validated = WebhookHeaders.validatedCopy(source);
        source.put("X-Other", "changed");

        assertThat(validated).containsOnlyKeys("X-Signature");
        assertThatThrownBy(() -> validated.put("X-New", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsCaseInsensitiveDuplicateNames() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Signature", "first");
        headers.put("x-signature", "second");

        assertThatThrownBy(() -> WebhookHeaders.validatedCopy(headers))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void rejectsInvalidHeaderNamesAndControlCharacters() {
        assertThatThrownBy(() -> WebhookHeaders.validatedCopy(Map.of("Bad Header", "value")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebhookHeaders.validatedCopy(Map.of("X-Test", "safe\r\nInjected: yes")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExcessiveHeaderCountAndValueLength() {
        Map<String, String> many = new LinkedHashMap<>();
        for (int i = 0; i <= WebhookHeaders.MAX_HEADER_COUNT; i++) {
            many.put("X-Test-" + i, "v");
        }
        assertThatThrownBy(() -> WebhookHeaders.validatedCopy(many))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> WebhookHeaders.validatedCopy(
                Map.of("X-Test", "x".repeat(WebhookHeaders.MAX_VALUE_LENGTH + 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAggregateHeaderSizeOverflow() {
        assertThatThrownBy(() -> WebhookHeaders.validatedCopy(Map.of(
                "X-Test", "x".repeat(WebhookHeaders.MAX_TOTAL_CHARACTERS))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
