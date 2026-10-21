package tech.kayys.numou.mobility.wallet.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record PayoutPaid(
        UUID eventId,
        Instant occurredAt,
        UUID payoutId,
        UUID driverId,
        BigDecimal amount) implements DomainEvent {

    public static PayoutPaid at(Instant now, UUID payoutId, UUID driverId, BigDecimal amount) {
        return new PayoutPaid(UUID.randomUUID(), now, payoutId, driverId, amount);
    }

    @Override
    public String eventType() {
        return "mobility.wallet.payout-paid";
    }
}
