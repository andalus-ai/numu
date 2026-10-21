package tech.kayys.numou.education.school.domain.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.school.domain.AttendanceSheetId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record AttendanceSheetLocked(
        UUID eventId,
        Instant occurredAt,
        AttendanceSheetId sheetId,
        TenantId tenantId,
        ClassroomId classroomId,
        LocalDate date
) implements DomainEvent {
    public AttendanceSheetLocked(AttendanceSheetId sheetId, TenantId tenantId, ClassroomId classroomId, LocalDate date) {
        this(UUID.randomUUID(), Instant.now(), sheetId, tenantId, classroomId, date);
    }

    @Override
    public String eventType() {
        return "school.attendance.locked";
    }
}
