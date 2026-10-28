package tech.kayys.numou.wallet.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record WalletUnfrozen(
        UUID eventId,
        Instant occurredAt,
        UUID walletId) implements DomainEvent {

    public static WalletUnfrozen at(Instant now, UUID walletId) {
        return new WalletUnfrozen(UUID.randomUUID(), now, walletId);
    }

    @Override
    public String eventType() {
        return "mobility.wallet.unfrozen";
    }
}
