package tech.kayys.numou.workforce.domain.paymentaccount.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.paymentaccount.PaymentAccountId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record PaymentAccountDeactivated(
        UUID eventId,
        Instant occurredAt,
        PaymentAccountId paymentAccountId,
        WorkerId workerId
) implements DomainEvent {
    public PaymentAccountDeactivated(PaymentAccountId paymentAccountId, WorkerId workerId) {
        this(UUID.randomUUID(), Instant.now(), paymentAccountId, workerId);
    }

    @Override
    public String eventType() { return "workforce.paymentaccount.deactivated"; }
}
