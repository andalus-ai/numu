package tech.kayys.numou.workforce.domain.talent;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.talent.event.TalentReviewCycleOpened;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class TalentReviewCycle extends AbstractAggregateRoot<TalentReviewCycleId> {

    private final TenantId tenantId;
    private final String code;
    private String name;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private TalentReviewCycleStatus status;

    private TalentReviewCycle(
            TalentReviewCycleId id,
            TenantId tenantId,
            String code,
            String name,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart must not be null");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        this.status = TalentReviewCycleStatus.DRAFT;
    }

    public static TalentReviewCycle create(
            TalentReviewCycleId id,
            TenantId tenantId,
            String code,
            String name,
            LocalDate periodStart,
            LocalDate periodEnd
    ) {
        return new TalentReviewCycle(id, tenantId, code, name, periodStart, periodEnd);
    }

    public void open() {
        this.status = TalentReviewCycleStatus.OPEN;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new TalentReviewCycleOpened(getId(), tenantId, code));
    }

    public void close() {
        this.status = TalentReviewCycleStatus.CLOSED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public TalentReviewCycleStatus getStatus() { return status; }
}
