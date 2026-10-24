package tech.kayys.numou.omnichannel.core;

import java.util.Map;
import java.util.Objects;

/**
 * Transient provider-verification input. Raw body and headers are sensitive;
 * implementations must not retain them or include them in exceptions/logs.
 */
public record ProviderWebhookVerificationRequest(
        String providerId,
        String providerAccountId,
        Map<String, String> headers,
        byte[] rawBody) {

    public ProviderWebhookVerificationRequest {
        providerId = required(providerId, "providerId");
        providerAccountId = required(providerAccountId, "providerAccountId");
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers must not be null"));
        rawBody = Objects.requireNonNull(rawBody, "rawBody must not be null").clone();
    }

    @Override
    public byte[] rawBody() {
        return rawBody.clone();
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 200) {
            throw new IllegalArgumentException(field + " must contain 1 to 200 characters");
        }
        return normalized;
    }
}
