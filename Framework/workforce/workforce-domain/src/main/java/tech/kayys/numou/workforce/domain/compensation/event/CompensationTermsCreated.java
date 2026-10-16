package tech.kayys.numou.workforce.domain.compensation.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.valueobject.Money;
import tech.kayys.numou.workforce.domain.compensation.CompensationTermsId;
import tech.kayys.numou.workforce.domain.compensation.PayFrequency;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CompensationTermsCreated(
        UUID eventId,
        Instant occurredAt,
        CompensationTermsId compensationTermsId,
        EmploymentId employmentId,
        Money basePay,
        PayFrequency payFrequency,
        LocalDate effectiveFrom
) implements DomainEvent {
    public CompensationTermsCreated(
            CompensationTermsId id,
            EmploymentId employmentId,
            Money basePay,
            PayFrequency payFrequency,
            LocalDate effectiveFrom
    ) {
        this(UUID.randomUUID(), Instant.now(), id, employmentId, basePay, payFrequency, effectiveFrom);
    }

    @Override
    public String eventType() {
        return "workforce.compensation.terms-created";
    }
}
