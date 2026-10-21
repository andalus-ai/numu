package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.education.core.Gender;
import tech.kayys.numou.education.core.Nisn;
import tech.kayys.numou.education.core.Religion;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.event.StudentRegistered;
import tech.kayys.numou.education.school.domain.event.StudentStatusChanged;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** A student (siswa). NISN may be absent for newly admitted students and assigned later. */
public final class Student extends AbstractAggregateRoot<StudentId> {

    private final TenantId tenantId;
    private final SchoolId schoolId;
    private final String fullName;
    private final Gender gender;
    private final LocalDate birthDate;
    private final String birthPlace;
    private final Religion religion;
    private Nisn nisn;
    private String nis;
    private StudentStatus status = StudentStatus.ACTIVE;

    private Student(StudentId id, TenantId tenantId, SchoolId schoolId, String fullName, Gender gender,
                    LocalDate birthDate, String birthPlace, Religion religion) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.schoolId = Objects.requireNonNull(schoolId, "schoolId must not be null");
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("fullName must not be blank");
        this.fullName = fullName.trim();
        this.gender = Objects.requireNonNull(gender, "gender must not be null");
        this.birthDate = Objects.requireNonNull(birthDate, "birthDate must not be null");
        if (!birthDate.isBefore(LocalDate.now())) throw new IllegalArgumentException("birthDate must be in the past");
        this.birthPlace = birthPlace;
        this.religion = Objects.requireNonNull(religion, "religion must not be null");
    }

    public static Student register(StudentId id, TenantId tenantId, SchoolId schoolId, String fullName, Gender gender,
                                   LocalDate birthDate, String birthPlace, Religion religion, Nisn nisn, String nis) {
        Student s = new Student(id, tenantId, schoolId, fullName, gender, birthDate, birthPlace, religion);
        s.nisn = nisn;
        s.nis = nis;
        s.raise(new StudentRegistered(id, tenantId, schoolId, s.fullName));
        return s;
    }

    public void assignNisn(Nisn nisn) {
        if (this.nisn != null) throw new IllegalStateException("NISN already assigned");
        this.nisn = Objects.requireNonNull(nisn, "nisn must not be null");
        touch();
    }

    public void graduate() { transition(StudentStatus.GRADUATED); }
    public void transferOut() { transition(StudentStatus.TRANSFERRED); }
    public void dropOut() { transition(StudentStatus.DROPPED_OUT); }

    private void transition(StudentStatus next) {
        if (status != StudentStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE students can change status; current: " + status);
        }
        status = next;
        touch();
        raise(new StudentStatusChanged(getId(), tenantId, next.name()));
    }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public SchoolId getSchoolId() { return schoolId; }
    public String getFullName() { return fullName; }
    public Gender getGender() { return gender; }
    public LocalDate getBirthDate() { return birthDate; }
    public String getBirthPlace() { return birthPlace; }
    public Religion getReligion() { return religion; }
    public Nisn getNisn() { return nisn; }
    public String getNis() { return nis; }
    public StudentStatus getStatus() { return status; }
    public boolean isActive() { return status == StudentStatus.ACTIVE; }
}
