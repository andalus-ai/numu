package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record SantriWalletId(UUID value) implements DomainId<UUID> {
    public static SantriWalletId of(UUID v) { return new SantriWalletId(v); }
    public static SantriWalletId generate() { return new SantriWalletId(UUID.randomUUID()); }
}
