package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.event.AttendanceSheetLocked;
import tech.kayys.numou.education.school.domain.event.AttendanceSheetOpened;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Daily attendance (absensi) for one classroom. Locked sheets are immutable. */
public final class AttendanceSheet extends AbstractAggregateRoot<AttendanceSheetId> {

    private final TenantId tenantId;
    private final ClassroomId classroomId;
    private final LocalDate date;
    private final Map<StudentId, AttendanceStatus> entries = new LinkedHashMap<>();
    private boolean locked;

    private AttendanceSheet(AttendanceSheetId id, TenantId tenantId, ClassroomId classroomId, LocalDate date) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.date = Objects.requireNonNull(date, "date must not be null");
    }

    public static AttendanceSheet open(AttendanceSheetId id, TenantId tenantId, ClassroomId classroomId, LocalDate date) {
        AttendanceSheet s = new AttendanceSheet(id, tenantId, classroomId, date);
        s.raise(new AttendanceSheetOpened(id, tenantId, classroomId, date));
        return s;
    }

    public void mark(StudentId studentId, AttendanceStatus status) {
        if (locked) throw new IllegalStateException("Attendance sheet is locked");
        entries.put(Objects.requireNonNull(studentId), Objects.requireNonNull(status));
        incrementVersion();
        updatedAt = Instant.now();
    }

    public void lock() {
        if (locked) return;
        locked = true;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new AttendanceSheetLocked(getId(), tenantId, classroomId, date));
    }

    public Map<AttendanceStatus, Long> summary() {
        Map<AttendanceStatus, Long> m = new EnumMap<>(AttendanceStatus.class);
        for (AttendanceStatus s : AttendanceStatus.values()) m.put(s, 0L);
        entries.values().forEach(s -> m.merge(s, 1L, Long::sum));
        return m;
    }

    /** Share of marked students who were present, 0.0 when nobody is marked. */
    public double attendanceRate() {
        if (entries.isEmpty()) return 0.0;
        return (double) summary().get(AttendanceStatus.HADIR) / entries.size();
    }

    public TenantId getTenantId() { return tenantId; }
    public ClassroomId getClassroomId() { return classroomId; }
    public LocalDate getDate() { return date; }
    public boolean isLocked() { return locked; }
    public Map<StudentId, AttendanceStatus> getEntries() { return Collections.unmodifiableMap(entries); }
}
