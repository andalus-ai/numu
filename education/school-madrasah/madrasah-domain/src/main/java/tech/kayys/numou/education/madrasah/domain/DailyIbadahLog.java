package tech.kayys.numou.education.madrasah.domain;

import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.madrasah.domain.event.IbadahLogged;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** A student's worship and character log for one day (monitoring ibadah harian). */
public final class DailyIbadahLog extends AbstractAggregateRoot<IbadahLogId> {

    private final TenantId tenantId;
    private final StudentId studentId;
    private final LocalDate date;
    private final Set<Prayer> prayers = EnumSet.noneOf(Prayer.class);
    private boolean sunnahFast;
    private int tilawahPages;

    private DailyIbadahLog(IbadahLogId id, TenantId tenantId, StudentId studentId, LocalDate date) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.studentId = Objects.requireNonNull(studentId, "studentId must not be null");
        this.date = Objects.requireNonNull(date, "date must not be null");
    }

    public static DailyIbadahLog log(IbadahLogId id, TenantId tenantId, StudentId studentId, LocalDate date,
                                     Collection<Prayer> prayers, boolean sunnahFast, int tilawahPages) {
        DailyIbadahLog l = new DailyIbadahLog(id, tenantId, studentId, date);
        l.set(prayers, sunnahFast, tilawahPages);
        l.raise(new IbadahLogged(id, tenantId, studentId, date));
        return l;
    }

    /** Correct the same day's log. */
    public void update(Collection<Prayer> prayers, boolean sunnahFast, int tilawahPages) {
        set(prayers, sunnahFast, tilawahPages);
        incrementVersion();
        updatedAt = Instant.now();
        raise(new IbadahLogged(getId(), tenantId, studentId, date));
    }

    private void set(Collection<Prayer> newPrayers, boolean fast, int pages) {
        if (pages < 0) throw new IllegalArgumentException("tilawahPages must not be negative");
        prayers.clear();
        if (newPrayers != null) prayers.addAll(newPrayers);
        this.sunnahFast = fast;
        this.tilawahPages = pages;
    }

    /** Fraction of the five obligatory prayers performed, 0.0 to 1.0. */
    public double prayerCompletion() { return prayers.size() / (double) Prayer.values().length; }

    public TenantId getTenantId() { return tenantId; }
    public StudentId getStudentId() { return studentId; }
    public LocalDate getDate() { return date; }
    public Set<Prayer> getPrayers() { return Collections.unmodifiableSet(prayers); }
    public boolean isSunnahFast() { return sunnahFast; }
    public int getTilawahPages() { return tilawahPages; }
}
