package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.education.pesantren.domain.event.DisciplineRecorded;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;
import java.util.Objects;

/** A recorded violation (pelanggaran) with penalty points, kept as an append-only record. */
public final class DisciplineRecord extends AbstractAggregateRoot<DisciplineRecordId> {

    private final TenantId tenantId;
    private final SantriId santriId;
    private final LocalDate date;
    private final DisciplineCategory category;
    private final String description;
    private final int points;
    private final String recordedBy;

    private DisciplineRecord(DisciplineRecordId id, TenantId tenantId, SantriId santriId, LocalDate date,
                             DisciplineCategory category, String description, int points, String recordedBy) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.santriId = Objects.requireNonNull(santriId, "santriId must not be null");
        this.date = Objects.requireNonNull(date, "date must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description must not be blank");
        this.description = description.trim();
        if (points <= 0) throw new IllegalArgumentException("points must be positive");
        this.points = points;
        if (recordedBy == null || recordedBy.isBlank()) throw new IllegalArgumentException("recordedBy must not be blank");
        this.recordedBy = recordedBy.trim();
    }

    public static DisciplineRecord record(DisciplineRecordId id, TenantId tenantId, SantriId santriId, LocalDate date,
                                          DisciplineCategory category, String description, int points, String recordedBy) {
        DisciplineRecord r = new DisciplineRecord(id, tenantId, santriId, date, category, description, points, recordedBy);
        r.raise(new DisciplineRecorded(id, tenantId, santriId, points));
        return r;
    }

    public TenantId getTenantId() { return tenantId; }
    public SantriId getSantriId() { return santriId; }
    public LocalDate getDate() { return date; }
    public DisciplineCategory getCategory() { return category; }
    public String getDescription() { return description; }
    public int getPoints() { return points; }
    public String getRecordedBy() { return recordedBy; }
}
