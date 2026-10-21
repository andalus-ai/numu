package tech.kayys.numou.mobility.wallet.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.foundation.domain.event.DomainEvent;

public record TopUpMismatch(
        UUID eventId,
        Instant occurredAt,
        UUID intentId,
        UUID riderId,
        BigDecimal expected,
        BigDecimal received,
        String provider) implements DomainEvent {

    public static TopUpMismatch at(Instant now, UUID intentId, UUID riderId, BigDecimal expected, BigDecimal received, String provider) {
        return new TopUpMismatch(UUID.randomUUID(), now, intentId, riderId, expected, received, provider);
    }

    @Override
    public String eventType() {
        return "mobility.wallet.topup-mismatch";
    }
}
