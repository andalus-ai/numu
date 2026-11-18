package tech.kayys.numou.socialgraph.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for a social connection lifecycle. */
public record SocialConnectionId(UUID value) {
    public SocialConnectionId {
        Objects.requireNonNull(value, "value cannot be null");
    }
    public static SocialConnectionId generate() { return new SocialConnectionId(UUID.randomUUID()); }
    public static SocialConnectionId of(UUID value) { return new SocialConnectionId(value); }
    @Override public String toString() { return value.toString(); }
}
