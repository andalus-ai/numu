package tech.kayys.numou.leave.domain.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.leave.domain.LeaveBalanceId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LeaveBalanceAdjusted(
        UUID eventId,
        Instant occurredAt,
        LeaveBalanceId balanceId,
        BigDecimal amount,
        String reason
) implements DomainEvent {
    public LeaveBalanceAdjusted(LeaveBalanceId balanceId, BigDecimal amount, String reason) {
        this(UUID.randomUUID(), Instant.now(), balanceId, amount, reason);
    }
    @Override
    public String eventType() {
        return "leave.balance.adjusted";
    }
}
