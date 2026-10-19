package tech.kayys.numou.workforce.domain.payroll;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record PayrollRunId(UUID value) implements DomainId<UUID> {
    public PayrollRunId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PayrollRunId of(UUID value) { return new PayrollRunId(value); }
    public static PayrollRunId of(String value) { return new PayrollRunId(UUID.fromString(value)); }
    public static PayrollRunId generate() { return new PayrollRunId(UUID.randomUUID()); }

    @Override
    public String toString() { return value.toString(); }
}
