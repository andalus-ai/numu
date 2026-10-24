package tech.kayys.numou.omnichannel.core;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Transport-neutral webhook input after tenant/account routing, before trust verification. */
public record InboundWebhookRequest(ChannelContext context, String providerId, Map<String, String> headers, byte[] rawBody, String externalEventId, String eventType, Optional<Instant> providerOccurredAt) {
    public InboundWebhookRequest {
        Objects.requireNonNull(context, "context must not be null");
        providerId = required(providerId, "providerId");
        headers = Map.copyOf(Objects.requireNonNull(headers, "headers must not be null"));
        rawBody = Objects.requireNonNull(rawBody, "rawBody must not be null").clone();
        externalEventId = required(externalEventId, "externalEventId");
        eventType = required(eventType, "eventType");
        providerOccurredAt = Objects.requireNonNull(providerOccurredAt, "providerOccurredAt must not be null");
    }
    /** Returns the byte count without allocating another defensive copy. */
    public int rawBodyLength() { return rawBody.length; }
    @Override public byte[] rawBody() { return rawBody.clone(); }
    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 250) throw new IllegalArgumentException(field + " must contain 1 to 250 characters");
        return normalized;
    }
}
