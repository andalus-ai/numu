package tech.kayys.numou.omnichannel.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderWebhookVerificationRequestTest {
    @Test
    void defensivelyCopiesRawBodyOnInputAndOutput() {
        byte[] input = new byte[] {1, 2, 3};
        ProviderWebhookVerificationRequest request = new ProviderWebhookVerificationRequest(
                "provider", "account", Map.of("X-Signature", "redacted"), input);
        input[0] = 9;
        byte[] output = request.rawBody();
        output[1] = 9;
        assertThat(request.rawBody()).containsExactly(1, 2, 3);
    }

    @Test
    void rejectsBlankProviderIdentity() {
        assertThatThrownBy(() -> new ProviderWebhookVerificationRequest(
                " ", "account", Map.of(), new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
