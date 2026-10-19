package tech.kayys.numou.workforce.domain.attendance;

import tech.kayys.numou.foundation.domain.identifier.DomainId;
import java.util.UUID;

public record AttendanceRecordId(UUID value) implements DomainId<UUID> {
    public static AttendanceRecordId of(UUID value) { return new AttendanceRecordId(value); }
    public static AttendanceRecordId generate() { return new AttendanceRecordId(UUID.randomUUID()); }
}
