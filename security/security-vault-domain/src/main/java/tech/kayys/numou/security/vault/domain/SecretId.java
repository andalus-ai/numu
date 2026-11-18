package tech.kayys.numou.security.vault.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable logical identity; updates create new immutable versions. */
public record SecretId(UUID value) {
    public SecretId { Objects.requireNonNull(value, "value is required"); }
    public static SecretId generate() { return new SecretId(UUID.randomUUID()); }
    public static SecretId of(UUID value) { return new SecretId(value); }
    @Override public String toString() { return value.toString(); }
}
