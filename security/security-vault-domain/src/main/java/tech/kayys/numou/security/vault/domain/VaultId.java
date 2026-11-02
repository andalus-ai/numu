package tech.kayys.numou.security.vault.domain;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for a logical secret namespace. */
public record VaultId(UUID value) {
    public VaultId { Objects.requireNonNull(value, "value is required"); }
    public static VaultId generate() { return new VaultId(UUID.randomUUID()); }
    public static VaultId of(UUID value) { return new VaultId(value); }
    @Override public String toString() { return value.toString(); }
}
