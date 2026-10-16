package tech.kayys.syirkah.commerce.offering.domain;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;

import java.util.Objects;

public record ChannelId(String value) implements DomainId<String> {
    public ChannelId {
        Objects.requireNonNull(value, "channelId cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("channelId cannot be blank");
        }
    }

    public static ChannelId of(String value) {
        return new ChannelId(value);
    }
}
