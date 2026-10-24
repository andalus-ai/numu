package tech.kayys.numou.omnichannel.core;

import java.util.Objects;

/**
 * Opaque identifier for payload bytes stored by an infrastructure adapter.
 * This value must not contain the payload, credentials, or a public object URL.
 */
public record InboundPayloadReference(String value) {
    public InboundPayloadReference {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (value.isEmpty() || value.length() > 200) {
            throw new IllegalArgumentException("value must contain 1 to 200 characters");
        }
    }
}
