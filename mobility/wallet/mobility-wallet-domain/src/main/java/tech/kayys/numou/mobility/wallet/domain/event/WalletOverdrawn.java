package tech.kayys.numou.mobility.wallet.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record WalletOverdrawn(
        UUID eventId,
        Instant occurredAt,
        UUID walletId,
        String ownerKind,
        UUID ownerId,
        BigDecimal balance) implements DomainEvent {

    public static WalletOverdrawn at(Instant now, UUID walletId, String ownerKind, UUID ownerId, BigDecimal balance) {
        return new WalletOverdrawn(UUID.randomUUID(), now, walletId, ownerKind, ownerId, balance);
    }

    @Override
    public String eventType() {
        return "mobility.wallet.overdrawn";
    }
}
