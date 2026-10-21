package tech.kayys.numou.education.lms.domain;

import tech.kayys.numou.education.core.AcademicYear;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.core.Semester;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.lms.domain.event.CourseCreated;
import tech.kayys.numou.education.lms.domain.event.CoursePublished;
import tech.kayys.numou.education.lms.domain.event.StudentEnrolledInCourse;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** A course taught by one instructor for a term. Students can only enrol once it is published. */
public final class Course extends AbstractAggregateRoot<CourseId> {

    private final TenantId tenantId;
    private final SchoolId schoolId;
    private final TeacherId instructor;
    private final String code;
    private final AcademicYear academicYear;
    private final Semester semester;
    private String title;
    private CourseStatus status = CourseStatus.DRAFT;
    private final List<Lesson> lessons = new ArrayList<>();
    private final Set<StudentId> enrolled = new LinkedHashSet<>();

    private Course(CourseId id, TenantId tenantId, SchoolId schoolId, TeacherId instructor, String title, String code,
                   AcademicYear academicYear, Semester semester) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.schoolId = Objects.requireNonNull(schoolId, "schoolId must not be null");
        this.instructor = Objects.requireNonNull(instructor, "instructor must not be null");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("Course title must not be blank");
        this.title = title.trim();
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Course code must not be blank");
        this.code = code.trim();
        this.academicYear = Objects.requireNonNull(academicYear, "academicYear must not be null");
        this.semester = Objects.requireNonNull(semester, "semester must not be null");
    }

    public static Course create(CourseId id, TenantId tenantId, SchoolId schoolId, TeacherId instructor, String title,
                                String code, AcademicYear academicYear, Semester semester) {
        Course c = new Course(id, tenantId, schoolId, instructor, title, code, academicYear, semester);
        c.raise(new CourseCreated(id, tenantId, c.title));
        return c;
    }

    public Lesson addLesson(String lessonTitle, LessonType type, String contentRef) {
        if (status == CourseStatus.ARCHIVED) throw new IllegalStateException("Archived courses cannot change");
        Lesson l = new Lesson(UUID.randomUUID(), lessonTitle, type, contentRef, lessons.size() + 1);
        lessons.add(l);
        touch();
        return l;
    }

    public void publish() {
        if (status != CourseStatus.DRAFT) throw new IllegalStateException("Only DRAFT courses can be published");
        if (lessons.isEmpty()) throw new IllegalStateException("A course needs at least one lesson before publishing");
        status = CourseStatus.PUBLISHED;
        touch();
        raise(new CoursePublished(getId(), tenantId));
    }

    public void archive() {
        status = CourseStatus.ARCHIVED;
        touch();
    }

    public void enroll(StudentId studentId) {
        Objects.requireNonNull(studentId, "studentId must not be null");
        if (status != CourseStatus.PUBLISHED) throw new IllegalStateException("Course is not open for enrolment");
        if (!enrolled.add(studentId)) throw new IllegalStateException("Student already enrolled");
        touch();
        raise(new StudentEnrolledInCourse(getId(), tenantId, studentId));
    }

    public void unenroll(StudentId studentId) {
        if (!enrolled.remove(studentId)) throw new IllegalStateException("Student is not enrolled");
        touch();
    }

    public boolean isEnrolled(StudentId studentId) { return enrolled.contains(studentId); }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public SchoolId getSchoolId() { return schoolId; }
    public TeacherId getInstructor() { return instructor; }
    public String getTitle() { return title; }
    public String getCode() { return code; }
    public AcademicYear getAcademicYear() { return academicYear; }
    public Semester getSemester() { return semester; }
    public CourseStatus getStatus() { return status; }
    public List<Lesson> getLessons() { return Collections.unmodifiableList(lessons); }
    public Set<StudentId> getEnrolled() { return Collections.unmodifiableSet(enrolled); }
}
