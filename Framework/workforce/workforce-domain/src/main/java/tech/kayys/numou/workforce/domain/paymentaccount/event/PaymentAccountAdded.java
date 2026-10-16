package tech.kayys.numou.workforce.domain.paymentaccount.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.paymentaccount.PaymentAccountId;
import tech.kayys.numou.workforce.domain.paymentaccount.PaymentAccountPurpose;
import tech.kayys.numou.workforce.domain.paymentaccount.PaymentAccountType;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record PaymentAccountAdded(
        UUID eventId,
        Instant occurredAt,
        PaymentAccountId paymentAccountId,
        WorkerId workerId,
        PaymentAccountType type,
        PaymentAccountPurpose purpose
) implements DomainEvent {
    public PaymentAccountAdded(
            PaymentAccountId paymentAccountId,
            WorkerId workerId,
            PaymentAccountType type,
            PaymentAccountPurpose purpose
    ) {
        this(UUID.randomUUID(), Instant.now(), paymentAccountId, workerId, type, purpose);
    }

    @Override
    public String eventType() { return "workforce.paymentaccount.added"; }
}
