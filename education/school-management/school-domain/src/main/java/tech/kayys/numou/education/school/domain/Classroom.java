package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.education.core.AcademicYear;
import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.GradeLevel;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.school.domain.event.ClassroomCreated;
import tech.kayys.numou.education.school.domain.event.StudentEnrolledInClassroom;
import tech.kayys.numou.education.school.domain.event.StudentRemovedFromClassroom;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** A class group (rombongan belajar / rombel) for one academic year, with a capacity-bound roster. */
public final class Classroom extends AbstractAggregateRoot<ClassroomId> {

    private final TenantId tenantId;
    private final SchoolId schoolId;
    private final String name;
    private final GradeLevel gradeLevel;
    private final AcademicYear academicYear;
    private final int capacity;
    private final Set<StudentId> roster = new LinkedHashSet<>();
    private TeacherId homeroomTeacher;

    private Classroom(ClassroomId id, TenantId tenantId, SchoolId schoolId, String name, GradeLevel gradeLevel,
                      AcademicYear academicYear, int capacity) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.schoolId = Objects.requireNonNull(schoolId, "schoolId must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Classroom name must not be blank");
        this.name = name.trim();
        this.gradeLevel = Objects.requireNonNull(gradeLevel, "gradeLevel must not be null");
        this.academicYear = Objects.requireNonNull(academicYear, "academicYear must not be null");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public static Classroom create(ClassroomId id, TenantId tenantId, SchoolId schoolId, String name,
                                   GradeLevel gradeLevel, AcademicYear academicYear, int capacity) {
        Classroom c = new Classroom(id, tenantId, schoolId, name, gradeLevel, academicYear, capacity);
        c.raise(new ClassroomCreated(id, tenantId, schoolId, c.name));
        return c;
    }

    public void assignHomeroomTeacher(TeacherId teacherId) {
        this.homeroomTeacher = Objects.requireNonNull(teacherId, "teacherId must not be null");
        touch();
    }

    public void enroll(StudentId studentId) {
        Objects.requireNonNull(studentId, "studentId must not be null");
        if (roster.contains(studentId)) throw new IllegalStateException("Student already enrolled in " + name);
        if (roster.size() >= capacity) throw new IllegalStateException("Classroom " + name + " is full (capacity " + capacity + ")");
        roster.add(studentId);
        touch();
        raise(new StudentEnrolledInClassroom(getId(), tenantId, studentId));
    }

    public void remove(StudentId studentId) {
        if (!roster.remove(studentId)) throw new IllegalStateException("Student is not enrolled in " + name);
        touch();
        raise(new StudentRemovedFromClassroom(getId(), tenantId, studentId));
    }

    public boolean hasStudent(StudentId studentId) { return roster.contains(studentId); }
    public boolean isFull() { return roster.size() >= capacity; }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public SchoolId getSchoolId() { return schoolId; }
    public String getName() { return name; }
    public GradeLevel getGradeLevel() { return gradeLevel; }
    public AcademicYear getAcademicYear() { return academicYear; }
    public int getCapacity() { return capacity; }
    public TeacherId getHomeroomTeacher() { return homeroomTeacher; }
    public Set<StudentId> getRoster() { return Collections.unmodifiableSet(roster); }
}
