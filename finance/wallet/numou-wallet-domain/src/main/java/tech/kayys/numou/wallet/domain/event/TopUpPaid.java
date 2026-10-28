package tech.kayys.numou.wallet.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record TopUpPaid(
        UUID eventId,
        Instant occurredAt,
        UUID intentId,
        UUID riderId,
        BigDecimal amount,
        String currency,
        String provider) implements DomainEvent {

    public static TopUpPaid at(Instant now, UUID intentId, UUID riderId, BigDecimal amount, String currency, String provider) {
        return new TopUpPaid(UUID.randomUUID(), now, intentId, riderId, amount, currency, provider);
    }

    @Override
    public String eventType() {
        return "mobility.wallet.topup-paid";
    }
}
