package tech.kayys.numou.workforce.domain.employment.onboarding.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.employment.onboarding.OnboardingProcessId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.Instant;
import java.util.UUID;

public record OnboardingCompleted(
        UUID eventId,
        Instant occurredAt,
        OnboardingProcessId id,
        WorkerId workerId,
        EmploymentId employmentId
) implements DomainEvent {
    public OnboardingCompleted(OnboardingProcessId id, WorkerId workerId, EmploymentId employmentId) {
        this(UUID.randomUUID(), Instant.now(), id, workerId, employmentId);
    }
    @Override public String eventType() { return "workforce.employment.onboarding.completed"; }
}
