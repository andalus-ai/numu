package tech.kayys.syirkah.construction.domain.quality;

import tech.kayys.syirkah.foundation.domain.identifier.DomainId;
import java.util.Objects;
import java.util.UUID;

public record NonConformanceReportId(UUID value) implements DomainId<UUID> {
    public NonConformanceReportId { Objects.requireNonNull(value); }
    public static NonConformanceReportId generate() { return new NonConformanceReportId(UUID.randomUUID()); }
    public static NonConformanceReportId of(UUID value) { return new NonConformanceReportId(value); }
}
