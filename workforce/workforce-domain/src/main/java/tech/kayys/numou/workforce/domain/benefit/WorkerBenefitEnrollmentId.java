package tech.kayys.numou.workforce.domain.benefit;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record WorkerBenefitEnrollmentId(UUID value) implements DomainId<UUID> {
    public WorkerBenefitEnrollmentId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static WorkerBenefitEnrollmentId of(UUID value) { return new WorkerBenefitEnrollmentId(value); }
    public static WorkerBenefitEnrollmentId of(String value) { return new WorkerBenefitEnrollmentId(UUID.fromString(value)); }
    public static WorkerBenefitEnrollmentId generate() { return new WorkerBenefitEnrollmentId(UUID.randomUUID()); }

    @Override
    public String toString() { return value.toString(); }
}
