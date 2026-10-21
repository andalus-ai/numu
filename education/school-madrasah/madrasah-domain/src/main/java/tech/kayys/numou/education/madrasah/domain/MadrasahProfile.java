package tech.kayys.numou.education.madrasah.domain;

import tech.kayys.numou.education.core.EducationLevel;
import tech.kayys.numou.education.core.SchoolId;
import tech.kayys.numou.education.madrasah.domain.event.MadrasahProfileRegistered;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Madrasah-specific profile that extends a general school (referenced by {@link SchoolId}).
 * The school itself stays owned by the school-management context.
 */
public final class MadrasahProfile extends AbstractAggregateRoot<MadrasahProfileId> {

    private final TenantId tenantId;
    private final SchoolId schoolId;
    private final Nsm nsm;
    private final EducationLevel level;
    private final MadrasahOwnership ownership;
    private final Set<PaiSubject> paiSubjects = EnumSet.allOf(PaiSubject.class);
    private boolean tahfidzProgram;

    private MadrasahProfile(MadrasahProfileId id, TenantId tenantId, SchoolId schoolId, Nsm nsm,
                            EducationLevel level, MadrasahOwnership ownership) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.schoolId = Objects.requireNonNull(schoolId, "schoolId must not be null");
        this.nsm = Objects.requireNonNull(nsm, "nsm must not be null");
        this.level = Objects.requireNonNull(level, "level must not be null");
        if (!level.isMadrasah()) {
            throw new IllegalArgumentException(level + " is not a madrasah level (use RA, MI, MTS or MA)");
        }
        this.ownership = Objects.requireNonNull(ownership, "ownership must not be null");
    }

    public static MadrasahProfile register(MadrasahProfileId id, TenantId tenantId, SchoolId schoolId, Nsm nsm,
                                           EducationLevel level, MadrasahOwnership ownership) {
        MadrasahProfile p = new MadrasahProfile(id, tenantId, schoolId, nsm, level, ownership);
        p.raise(new MadrasahProfileRegistered(id, tenantId, schoolId, nsm.value(), level));
        return p;
    }

    public void enableTahfidzProgram() {
        tahfidzProgram = true;
        touch();
    }

    /** Narrow the PAI subjects offered; a madrasah must keep at least one. */
    public void offerPaiSubjects(Set<PaiSubject> subjects) {
        if (subjects == null || subjects.isEmpty()) throw new IllegalArgumentException("At least one PAI subject is required");
        paiSubjects.clear();
        paiSubjects.addAll(subjects);
        touch();
    }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public SchoolId getSchoolId() { return schoolId; }
    public Nsm getNsm() { return nsm; }
    public EducationLevel getLevel() { return level; }
    public MadrasahOwnership getOwnership() { return ownership; }
    public Set<PaiSubject> getPaiSubjects() { return Collections.unmodifiableSet(paiSubjects); }
    public boolean hasTahfidzProgram() { return tahfidzProgram; }
}
