package tech.kayys.syirkah.accounting.domain.identifier;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record FiscalYearId(UUID value) implements DomainId<UUID> {
    public FiscalYearId {
        Objects.requireNonNull(value, "FiscalYearId value cannot be null");
    }
    public UUID getValue() { return value; }
    public static FiscalYearId generate() { return new FiscalYearId(UUID.randomUUID()); }
    public static FiscalYearId of(UUID value) { return new FiscalYearId(value); }
    public static FiscalYearId of(String value) { return new FiscalYearId(UUID.fromString(value)); }
}
