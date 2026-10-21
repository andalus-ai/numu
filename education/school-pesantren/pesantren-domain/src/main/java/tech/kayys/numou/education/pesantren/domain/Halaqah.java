package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.pesantren.domain.event.HalaqahCreated;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** A halaqah (study circle) led by an ustadz, typically for one kitab. */
public final class Halaqah extends AbstractAggregateRoot<HalaqahId> {

    private final TenantId tenantId;
    private final PesantrenId pesantrenId;
    private final String name;
    private final String kitabTitle;
    private final StudyMethod method;
    private final TeacherId ustadz;
    private final DayOfWeek day;
    private final LocalTime startTime;
    private final int capacity;
    private final Set<SantriId> members = new LinkedHashSet<>();

    private Halaqah(HalaqahId id, TenantId tenantId, PesantrenId pesantrenId, String name, String kitabTitle,
                    StudyMethod method, TeacherId ustadz, DayOfWeek day, LocalTime startTime, int capacity) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.pesantrenId = Objects.requireNonNull(pesantrenId, "pesantrenId must not be null");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Halaqah name must not be blank");
        this.name = name.trim();
        this.kitabTitle = kitabTitle;
        this.method = Objects.requireNonNull(method, "method must not be null");
        this.ustadz = Objects.requireNonNull(ustadz, "ustadz must not be null");
        this.day = Objects.requireNonNull(day, "day must not be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public static Halaqah create(HalaqahId id, TenantId tenantId, PesantrenId pesantrenId, String name, String kitabTitle,
                                 StudyMethod method, TeacherId ustadz, DayOfWeek day, LocalTime startTime, int capacity) {
        Halaqah h = new Halaqah(id, tenantId, pesantrenId, name, kitabTitle, method, ustadz, day, startTime, capacity);
        h.raise(new HalaqahCreated(id, tenantId, h.name));
        return h;
    }

    public void join(SantriId santriId) {
        Objects.requireNonNull(santriId, "santriId must not be null");
        if (members.contains(santriId)) throw new IllegalStateException("Santri already in halaqah " + name);
        if (members.size() >= capacity) throw new IllegalStateException("Halaqah " + name + " is full");
        members.add(santriId);
        touch();
    }

    public void leave(SantriId santriId) {
        if (!members.remove(santriId)) throw new IllegalStateException("Santri is not in halaqah " + name);
        touch();
    }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public PesantrenId getPesantrenId() { return pesantrenId; }
    public String getName() { return name; }
    public String getKitabTitle() { return kitabTitle; }
    public StudyMethod getMethod() { return method; }
    public TeacherId getUstadz() { return ustadz; }
    public DayOfWeek getDay() { return day; }
    public LocalTime getStartTime() { return startTime; }
    public int getCapacity() { return capacity; }
    public Set<SantriId> getMembers() { return Collections.unmodifiableSet(members); }
}
