package tech.kayys.syirkah.commerce.promotion.domain.context;

import java.util.Objects;

/** Opaque sales channel identity for promotion evaluation. */
public record ChannelId(String value) {

    public ChannelId {
        Objects.requireNonNull(value, "value cannot be null");
        value = value.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static ChannelId of(String value) {
        return new ChannelId(value);
    }
}
