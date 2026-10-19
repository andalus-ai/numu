package tech.kayys.numou.workforce.domain.payslip;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.Objects;
import java.util.UUID;

public record PayslipId(UUID value) implements DomainId<UUID> {
    public PayslipId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PayslipId of(UUID value) { return new PayslipId(value); }
    public static PayslipId of(String value) { return new PayslipId(UUID.fromString(value)); }
    public static PayslipId generate() { return new PayslipId(UUID.randomUUID()); }

    @Override
    public String toString() { return value.toString(); }
}
