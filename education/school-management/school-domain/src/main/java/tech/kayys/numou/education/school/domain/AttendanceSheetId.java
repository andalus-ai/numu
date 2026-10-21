package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.foundation.domain.identifier.DomainId;

import java.util.UUID;

public record AttendanceSheetId(UUID value) implements DomainId<UUID> {
    public static AttendanceSheetId of(UUID v) { return new AttendanceSheetId(v); }
    public static AttendanceSheetId generate() { return new AttendanceSheetId(UUID.randomUUID()); }
}
