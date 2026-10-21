package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.education.core.Gender;
import tech.kayys.numou.education.core.Nuptk;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.school.domain.event.TeacherRegistered;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** A teacher (guru). NUPTK is optional: not every educator has one. */
public final class Teacher extends AbstractAggregateRoot<TeacherId> {

    private final TenantId tenantId;
    private final SchoolId schoolId;
    private final String fullName;
    private final Gender gender;
    private final Nuptk nuptk;
    private final EmploymentType employmentType;
    private boolean active = true;

    private Teacher(TeacherId id, TenantId tenantId, SchoolId schoolId, String fullName, Gender gender,
                    Nuptk nuptk, EmploymentType employmentType) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.schoolId = Objects.requireNonNull(schoolId, "schoolId must not be null");
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("fullName must not be blank");
        this.fullName = fullName.trim();
        this.gender = Objects.requireNonNull(gender, "gender must not be null");
        this.nuptk = nuptk;
        this.employmentType = Objects.requireNonNull(employmentType, "employmentType must not be null");
    }

    public static Teacher register(TeacherId id, TenantId tenantId, SchoolId schoolId, String fullName, Gender gender,
                                   Nuptk nuptk, EmploymentType employmentType) {
        Teacher t = new Teacher(id, tenantId, schoolId, fullName, gender, nuptk, employmentType);
        t.raise(new TeacherRegistered(id, tenantId, schoolId, t.fullName));
        return t;
    }

    public void deactivate() {
        active = false;
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public SchoolId getSchoolId() { return schoolId; }
    public String getFullName() { return fullName; }
    public Gender getGender() { return gender; }
    public Nuptk getNuptk() { return nuptk; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public boolean isActive() { return active; }
}
