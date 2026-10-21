package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.education.core.Gender;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.pesantren.domain.event.SantriRegistered;
import tech.kayys.numou.education.pesantren.domain.event.SantriStatusChanged;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** A pesantren student (santri). May optionally link to a formal {@link StudentId}. */
public final class Santri extends AbstractAggregateRoot<SantriId> {

    private final TenantId tenantId;
    private final PesantrenId pesantrenId;
    private final String santriNumber;
    private final String fullName;
    private final Gender gender;
    private final LocalDate birthDate;
    private final Residency residency;
    private final String guardianName;
    private final String guardianPhone;
    private StudentId linkedStudent;
    private SantriStatus status = SantriStatus.ACTIVE;
    private DormRoomId roomId;

    private Santri(SantriId id, TenantId tenantId, PesantrenId pesantrenId, String santriNumber, String fullName,
                   Gender gender, LocalDate birthDate, Residency residency, String guardianName, String guardianPhone) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.pesantrenId = Objects.requireNonNull(pesantrenId, "pesantrenId must not be null");
        if (santriNumber == null || santriNumber.isBlank()) throw new IllegalArgumentException("santriNumber must not be blank");
        this.santriNumber = santriNumber.trim();
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("fullName must not be blank");
        this.fullName = fullName.trim();
        this.gender = Objects.requireNonNull(gender, "gender must not be null");
        this.birthDate = Objects.requireNonNull(birthDate, "birthDate must not be null");
        this.residency = Objects.requireNonNull(residency, "residency must not be null");
        if (guardianName == null || guardianName.isBlank()) throw new IllegalArgumentException("guardianName must not be blank");
        this.guardianName = guardianName.trim();
        this.guardianPhone = guardianPhone;
    }

    public static Santri register(SantriId id, TenantId tenantId, PesantrenId pesantrenId, String santriNumber,
                                  String fullName, Gender gender, LocalDate birthDate, Residency residency,
                                  String guardianName, String guardianPhone) {
        Santri s = new Santri(id, tenantId, pesantrenId, santriNumber, fullName, gender, birthDate, residency,
                guardianName, guardianPhone);
        s.raise(new SantriRegistered(id, tenantId, pesantrenId, s.fullName));
        return s;
    }

    public void linkFormalStudent(StudentId studentId) {
        this.linkedStudent = Objects.requireNonNull(studentId, "studentId must not be null");
        touch();
    }

    /** Only active mukim santri live in an asrama room. */
    public void assignRoom(DormRoomId room) {
        if (status != SantriStatus.ACTIVE) throw new IllegalStateException("Only ACTIVE santri can be housed");
        if (residency != Residency.MUKIM) throw new IllegalStateException("Only MUKIM santri can be housed in asrama");
        if (roomId != null) throw new IllegalStateException("Santri already has a room; vacate it first");
        this.roomId = Objects.requireNonNull(room, "room must not be null");
        touch();
    }

    public void clearRoom() {
        this.roomId = null;
        touch();
    }

    public void graduate() { leave(SantriStatus.ALUMNI); }

    public void withdraw() { leave(SantriStatus.WITHDRAWN); }

    private void leave(SantriStatus next) {
        if (status != SantriStatus.ACTIVE) throw new IllegalStateException("Santri is not ACTIVE; current: " + status);
        status = next;
        roomId = null;
        touch();
        raise(new SantriStatusChanged(getId(), tenantId, next.name()));
    }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public boolean isActive() { return status == SantriStatus.ACTIVE; }
    public TenantId getTenantId() { return tenantId; }
    public PesantrenId getPesantrenId() { return pesantrenId; }
    public String getSantriNumber() { return santriNumber; }
    public String getFullName() { return fullName; }
    public Gender getGender() { return gender; }
    public LocalDate getBirthDate() { return birthDate; }
    public Residency getResidency() { return residency; }
    public String getGuardianName() { return guardianName; }
    public String getGuardianPhone() { return guardianPhone; }
    public StudentId getLinkedStudent() { return linkedStudent; }
    public SantriStatus getStatus() { return status; }
    public DormRoomId getRoomId() { return roomId; }
}
