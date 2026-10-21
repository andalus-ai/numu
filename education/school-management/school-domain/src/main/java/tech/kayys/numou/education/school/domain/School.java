package tech.kayys.numou.education.school.domain;

import tech.kayys.numou.education.core.Curriculum;
import tech.kayys.numou.education.core.EducationLevel;
import tech.kayys.numou.education.core.Npsn;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.school.domain.event.SchoolCurriculumChanged;
import tech.kayys.numou.education.school.domain.event.SchoolRegistered;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** A school (satuan pendidikan) identified nationally by its NPSN. */
public final class School extends AbstractAggregateRoot<SchoolId> {

    private final TenantId tenantId;
    private final Npsn npsn;
    private final EducationLevel level;
    private final SchoolOwnership ownership;
    private String name;
    private Curriculum curriculum;
    private boolean active = true;

    private School(SchoolId id, TenantId tenantId, Npsn npsn, String name, EducationLevel level,
                   Curriculum curriculum, SchoolOwnership ownership) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.npsn = Objects.requireNonNull(npsn, "npsn must not be null");
        this.name = requireName(name);
        this.level = Objects.requireNonNull(level, "level must not be null");
        this.curriculum = Objects.requireNonNull(curriculum, "curriculum must not be null");
        this.ownership = Objects.requireNonNull(ownership, "ownership must not be null");
    }

    public static School register(SchoolId id, TenantId tenantId, Npsn npsn, String name, EducationLevel level,
                                  Curriculum curriculum, SchoolOwnership ownership) {
        School s = new School(id, tenantId, npsn, name, level, curriculum, ownership);
        s.raise(new SchoolRegistered(id, tenantId, npsn.value(), s.name, level));
        return s;
    }

    public void rename(String newName) {
        this.name = requireName(newName);
        touch();
    }

    public void changeCurriculum(Curriculum newCurriculum) {
        Objects.requireNonNull(newCurriculum, "curriculum must not be null");
        if (newCurriculum == this.curriculum) return;
        this.curriculum = newCurriculum;
        touch();
        raise(new SchoolCurriculumChanged(getId(), tenantId, newCurriculum));
    }

    public void deactivate() {
        this.active = false;
        touch();
    }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("School name must not be blank");
        return name.trim();
    }

    public TenantId getTenantId() { return tenantId; }
    public Npsn getNpsn() { return npsn; }
    public String getName() { return name; }
    public EducationLevel getLevel() { return level; }
    public Curriculum getCurriculum() { return curriculum; }
    public SchoolOwnership getOwnership() { return ownership; }
    public boolean isActive() { return active; }
}
