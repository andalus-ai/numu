package tech.kayys.syirkah.construction.domain.closeout.event;

import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinalAccountSettled(
        UUID eventId,
        Instant occurredAt,
        UUID finalAccountId,
        UUID contractId,
        BigDecimal finalContractAmount
) implements DomainEvent {
    @Override public String eventType() { return "construction.final-account-settled"; }
}
