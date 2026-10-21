package tech.kayys.numou.mobility.wallet.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record WalletFrozen(
        UUID eventId,
        Instant occurredAt,
        UUID walletId,
        String reason) implements DomainEvent {

    public static WalletFrozen at(Instant now, UUID walletId, String reason) {
        return new WalletFrozen(UUID.randomUUID(), now, walletId, reason);
    }

    @Override
    public String eventType() {
        return "mobility.wallet.frozen";
    }
}
