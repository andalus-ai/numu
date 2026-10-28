package tech.kayys.numou.wallet.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record PayoutFailed(
        UUID eventId,
        Instant occurredAt,
        UUID payoutId,
        UUID driverId,
        BigDecimal amount,
        String reason) implements DomainEvent {

    public static PayoutFailed at(Instant now, UUID payoutId, UUID driverId, BigDecimal amount, String reason) {
        return new PayoutFailed(UUID.randomUUID(), now, payoutId, driverId, amount, reason);
    }

    @Override
    public String eventType() {
        return "mobility.wallet.payout-failed";
    }
}
