package tech.kayys.numou.wallet.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic: one wallet per (owner kind, owner id). Two requests that both try to create a user's first wallet
 * collide on the same id and the loser reloads, instead of the user ending up with two wallets.
 */
public record WalletId(UUID value) implements DomainId<UUID>, Serializable {

    public WalletId {
        Objects.requireNonNull(value, "WalletId value must not be null");
    }

    public static WalletId forOwner(OwnerKind kind, UUID ownerId) {
        return new WalletId(UUID.nameUUIDFromBytes(("wallet:" + kind + ":" + ownerId).getBytes(StandardCharsets.UTF_8)));
    }

    public static WalletId of(UUID value) {
        return new WalletId(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
