package tech.kayys.numou.workforce.domain.learning;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.learning.event.LearningOfferingCompleted;
import tech.kayys.numou.workforce.domain.learning.event.LearningOfferingCreated;
import tech.kayys.numou.workforce.domain.learning.event.LearningOfferingOpened;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public final class LearningOffering extends AbstractAggregateRoot<LearningOfferingId> {

    private final TenantId tenantId;
    private final LearningProgramId programId;
    private final String code;
    private LocalDate startDate;
    private LocalDate endDate;
    private int capacity;
    private LearningOfferingStatus status;
    private LearningProviderRef providerRef;

    private LearningOffering(
            LearningOfferingId id,
            TenantId tenantId,
            LearningProgramId programId,
            String code,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            LearningProviderRef providerRef
    ) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.programId = Objects.requireNonNull(programId, "programId must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate must not be null");
        this.capacity = capacity;
        this.providerRef = providerRef;
        this.status = LearningOfferingStatus.DRAFT;
    }

    public static LearningOffering create(
            LearningOfferingId id,
            TenantId tenantId,
            LearningProgramId programId,
            String code,
            LocalDate startDate,
            LocalDate endDate,
            int capacity,
            LearningProviderRef providerRef
    ) {
        LearningOffering offering = new LearningOffering(id, tenantId, programId, code, startDate, endDate, capacity, providerRef);
        offering.raise(new LearningOfferingCreated(id, tenantId, programId, code));
        return offering;
    }

    public void open() {
        if (status != LearningOfferingStatus.DRAFT) {
            throw new IllegalStateException("Only draft offerings can be opened");
        }
        this.status = LearningOfferingStatus.OPEN;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new LearningOfferingOpened(getId()));
    }

    public void markFull() {
        if (status != LearningOfferingStatus.OPEN) {
            throw new IllegalStateException("Only open offerings can be marked full");
        }
        this.status = LearningOfferingStatus.FULL;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void start() {
        if (status != LearningOfferingStatus.OPEN && status != LearningOfferingStatus.FULL) {
            throw new IllegalStateException("Cannot start offering in status: " + status);
        }
        this.status = LearningOfferingStatus.IN_PROGRESS;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public void complete() {
        if (status != LearningOfferingStatus.IN_PROGRESS) {
            throw new IllegalStateException("Only in-progress offerings can be completed");
        }
        this.status = LearningOfferingStatus.COMPLETED;
        incrementVersion();
        this.updatedAt = Instant.now();
        raise(new LearningOfferingCompleted(getId()));
    }

    public void cancel() {
        if (status == LearningOfferingStatus.COMPLETED) {
            throw new IllegalStateException("Completed offerings cannot be cancelled");
        }
        this.status = LearningOfferingStatus.CANCELLED;
        incrementVersion();
        this.updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public LearningProgramId getProgramId() { return programId; }
    public String getCode() { return code; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getCapacity() { return capacity; }
    public LearningOfferingStatus getStatus() { return status; }
    public LearningProviderRef getProviderRef() { return providerRef; }
}
