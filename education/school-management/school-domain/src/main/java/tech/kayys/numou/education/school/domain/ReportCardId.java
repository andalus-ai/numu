package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record ReportCardId(UUID value) implements DomainId<UUID> {
    public static ReportCardId of(UUID v) { return new ReportCardId(v); }
    public static ReportCardId generate() { return new ReportCardId(UUID.randomUUID()); }
}
