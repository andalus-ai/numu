package tech.kayys.numou.education.school.domain.event;

import java.time.Instant;
import java.util.UUID;
import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.school.domain.LessonSlotId;
import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public record LessonSlotScheduled(
        UUID eventId,
        Instant occurredAt,
        LessonSlotId slotId,
        TenantId tenantId,
        ClassroomId classroomId,
        TeacherId teacherId,
        String subjectCode
) implements DomainEvent {
    public LessonSlotScheduled(LessonSlotId slotId, TenantId tenantId, ClassroomId classroomId, TeacherId teacherId, String subjectCode) {
        this(UUID.randomUUID(), Instant.now(), slotId, tenantId, classroomId, teacherId, subjectCode);
    }

    @Override
    public String eventType() {
        return "school.timetable.slot-scheduled";
    }
}
