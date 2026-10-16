package tech.kayys.syirkah.construction.domain.field;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record DailyFieldReportId(UUID value) implements DomainId<UUID> {
    public DailyFieldReportId { Objects.requireNonNull(value); }
    public static DailyFieldReportId generate() { return new DailyFieldReportId(UUID.randomUUID()); }
    public static DailyFieldReportId of(UUID value) { return new DailyFieldReportId(value); }
}
