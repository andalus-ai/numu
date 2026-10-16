package tech.kayys.syirkah.accounting.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record FiscalPeriodId(UUID value) implements DomainId<UUID> {
    public FiscalPeriodId {
        Objects.requireNonNull(value, "FiscalPeriodId value cannot be null");
    }
    public UUID getValue() { return value; }
    public static FiscalPeriodId generate() { return new FiscalPeriodId(UUID.randomUUID()); }
    public static FiscalPeriodId of(UUID value) { return new FiscalPeriodId(value); }
    public static FiscalPeriodId of(String value) { return new FiscalPeriodId(UUID.fromString(value)); }
}
