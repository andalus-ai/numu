package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.education.core.AcademicYear;
import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.Score;
import tech.kayys.numou.education.core.Semester;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.event.ReportCardFinalized;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rapor for one student and term. Once finalized it is a historical record and can no longer change;
 * corrections require a new issuance process outside this aggregate.
 */
public final class ReportCard extends AbstractAggregateRoot<ReportCardId> {

    private final TenantId tenantId;
    private final StudentId studentId;
    private final ClassroomId classroomId;
    private final AcademicYear academicYear;
    private final Semester semester;
    private final Map<String, SubjectResult> subjects = new LinkedHashMap<>();
    private int sick, permitted, absent;
    private String homeroomNote;
    private ReportCardStatus status = ReportCardStatus.DRAFT;
    private Instant finalizedAt;

    private ReportCard(ReportCardId id, TenantId tenantId, StudentId studentId, ClassroomId classroomId,
                       AcademicYear academicYear, Semester semester) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.studentId = Objects.requireNonNull(studentId, "studentId must not be null");
        this.classroomId = Objects.requireNonNull(classroomId, "classroomId must not be null");
        this.academicYear = Objects.requireNonNull(academicYear, "academicYear must not be null");
        this.semester = Objects.requireNonNull(semester, "semester must not be null");
    }

    public static ReportCard draft(ReportCardId id, TenantId tenantId, StudentId studentId, ClassroomId classroomId,
                                   AcademicYear academicYear, Semester semester) {
        return new ReportCard(id, tenantId, studentId, classroomId, academicYear, semester);
    }

    public void recordSubject(SubjectResult result) {
        requireDraft();
        subjects.put(Objects.requireNonNull(result).subjectCode(), result);
        touch();
    }

    public void recordAttendance(int sick, int permitted, int absent) {
        requireDraft();
        if (sick < 0 || permitted < 0 || absent < 0) throw new IllegalArgumentException("Attendance counts must not be negative");
        this.sick = sick;
        this.permitted = permitted;
        this.absent = absent;
        touch();
    }

    public void writeHomeroomNote(String note) {
        requireDraft();
        this.homeroomNote = note;
        touch();
    }

    public void finalizeCard() {
        requireDraft();
        if (subjects.isEmpty()) throw new IllegalStateException("Cannot finalize a report card without subject results");
        status = ReportCardStatus.FINALIZED;
        finalizedAt = Instant.now();
        touch();
        raise(new ReportCardFinalized(getId(), tenantId, studentId, academicYear, semester));
    }

    /** Mean knowledge score across subjects, or null when there are none. */
    public Score averageKnowledge() {
        if (subjects.isEmpty()) return null;
        BigDecimal sum = subjects.values().stream().map(r -> r.knowledge().value()).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Score(sum.divide(BigDecimal.valueOf(subjects.size()), 2, RoundingMode.HALF_UP));
    }

    private void requireDraft() {
        if (status != ReportCardStatus.DRAFT) throw new IllegalStateException("Report card is finalized");
    }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public StudentId getStudentId() { return studentId; }
    public ClassroomId getClassroomId() { return classroomId; }
    public AcademicYear getAcademicYear() { return academicYear; }
    public Semester getSemester() { return semester; }
    public Map<String, SubjectResult> getSubjects() { return Collections.unmodifiableMap(subjects); }
    public int getSick() { return sick; }
    public int getPermitted() { return permitted; }
    public int getAbsent() { return absent; }
    public String getHomeroomNote() { return homeroomNote; }
    public ReportCardStatus getStatus() { return status; }
    public Instant getFinalizedAt() { return finalizedAt; }
}
