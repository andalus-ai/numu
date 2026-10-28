package tech.kayys.numou.wallet.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record PayoutRequested(
        UUID eventId,
        Instant occurredAt,
        UUID payoutId,
        UUID driverId,
        BigDecimal amount,
        String currency) implements DomainEvent {

    public static PayoutRequested at(Instant now, UUID payoutId, UUID driverId, BigDecimal amount, String currency) {
        return new PayoutRequested(UUID.randomUUID(), now, payoutId, driverId, amount, currency);
    }

    @Override
    public String eventType() {
        return "mobility.wallet.payout-requested";
    }
}
