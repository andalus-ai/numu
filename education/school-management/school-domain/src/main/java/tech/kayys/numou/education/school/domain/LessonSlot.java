package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.school.domain.event.LessonSlotScheduled;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/** One weekly timetable entry (jadwal pelajaran): a teacher teaching a subject to a classroom. */
public final class LessonSlot extends AbstractAggregateRoot<LessonSlotId> {

    private final TenantId tenantId;
    private final ClassroomId classroomId;
    private final TeacherId teacherId;
    private final String subjectCode;
    private final DayOfWeek day;
    private final LocalTime start;
    private final LocalTime end;
    private final String room;

    private LessonSlot(LessonSlotId id, TenantId tenantId, ClassroomId classroomId, TeacherId teacherId,
                       String subjectCode, DayOfWeek day, LocalTime start, LocalTime end, String room) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.teacherId = Objects.requireNonNull(teacherId, "teacherId must not be null");
        if (subjectCode == null || subjectCode.isBlank()) throw new IllegalArgumentException("subjectCode must not be blank");
        this.subjectCode = subjectCode.trim();
        this.day = Objects.requireNonNull(day, "day must not be null");
        this.start = Objects.requireNonNull(start, "start must not be null");
        this.end = Objects.requireNonNull(end, "end must not be null");
        if (!end.isAfter(start)) throw new IllegalArgumentException("end must be after start");
        this.room = room;
    }

    public static LessonSlot schedule(LessonSlotId id, TenantId tenantId, ClassroomId classroomId, TeacherId teacherId,
                                      String subjectCode, DayOfWeek day, LocalTime start, LocalTime end, String room) {
        LessonSlot s = new LessonSlot(id, tenantId, classroomId, teacherId, subjectCode, day, start, end, room);
        s.raise(new LessonSlotScheduled(id, tenantId, classroomId, teacherId, s.subjectCode));
        return s;
    }

    /** Same weekday and the time ranges intersect (touching ends do not overlap). */
    public boolean overlaps(LessonSlot other) {
        return day == other.day && start.isBefore(other.end) && other.start.isBefore(end);
    }

    public boolean overlaps(DayOfWeek otherDay, LocalTime otherStart, LocalTime otherEnd) {
        return day == otherDay && start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    public TenantId getTenantId() { return tenantId; }
    public ClassroomId getClassroomId() { return classroomId; }
    public TeacherId getTeacherId() { return teacherId; }
    public String getSubjectCode() { return subjectCode; }
    public DayOfWeek getDay() { return day; }
    public LocalTime getStart() { return start; }
    public LocalTime getEnd() { return end; }
    public String getRoom() { return room; }
}
