package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.pesantren.domain.event.HafalanRecorded;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.LocalDate;
import java.util.Objects;

/** One setoran or muraja'ah session of Qur'an memorisation, heard by an ustadz. */
public final class HafalanLog extends AbstractAggregateRoot<HafalanLogId> {

    private final TenantId tenantId;
    private final SantriId santriId;
    private final LocalDate date;
    private final HafalanType type;
    private final QuranRange range;
    private final HafalanResult result;
    private final TeacherId examiner;

    private HafalanLog(HafalanLogId id, TenantId tenantId, SantriId santriId, LocalDate date, HafalanType type,
                       QuranRange range, HafalanResult result, TeacherId examiner) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.santriId = Objects.requireNonNull(santriId, "santriId must not be null");
        this.date = Objects.requireNonNull(date, "date must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.range = Objects.requireNonNull(range, "range must not be null");
        this.result = Objects.requireNonNull(result, "result must not be null");
        this.examiner = examiner;
    }

    public static HafalanLog record(HafalanLogId id, TenantId tenantId, SantriId santriId, LocalDate date,
                                    HafalanType type, QuranRange range, HafalanResult result, TeacherId examiner) {
        HafalanLog l = new HafalanLog(id, tenantId, santriId, date, type, range, result, examiner);
        l.raise(new HafalanRecorded(id, tenantId, santriId, range.surah(), range.fromAyah(), range.toAyah()));
        return l;
    }

    /** A setoran baru only counts toward progress once it was recited fluently. */
    public boolean countsAsNewProgress() { return type == HafalanType.SETORAN_BARU && result == HafalanResult.LANCAR; }

    public TenantId getTenantId() { return tenantId; }
    public SantriId getSantriId() { return santriId; }
    public LocalDate getDate() { return date; }
    public HafalanType getType() { return type; }
    public QuranRange getRange() { return range; }
    public HafalanResult getResult() { return result; }
    public TeacherId getExaminer() { return examiner; }
}
