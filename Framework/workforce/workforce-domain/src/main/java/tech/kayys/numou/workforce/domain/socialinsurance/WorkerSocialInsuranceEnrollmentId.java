package tech.kayys.numou.workforce.domain.socialinsurance;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record WorkerSocialInsuranceEnrollmentId(UUID value) implements DomainId<UUID> {
    public WorkerSocialInsuranceEnrollmentId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static WorkerSocialInsuranceEnrollmentId of(UUID value) { return new WorkerSocialInsuranceEnrollmentId(value); }
    public static WorkerSocialInsuranceEnrollmentId of(String value) { return new WorkerSocialInsuranceEnrollmentId(UUID.fromString(value)); }
    public static WorkerSocialInsuranceEnrollmentId generate() { return new WorkerSocialInsuranceEnrollmentId(UUID.randomUUID()); }

    @Override
    public String toString() { return value.toString(); }
}
