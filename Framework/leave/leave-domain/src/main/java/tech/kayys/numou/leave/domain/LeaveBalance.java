package tech.kayys.numou.leave.domain;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.leave.domain.event.LeaveBalanceAdjusted;
import tech.kayys.numou.leave.domain.event.LeaveBalanceAllocated;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class LeaveBalance extends AbstractAggregateRoot<LeaveBalanceId> {

    private final UUID subjectId;
    private final UUID contextId;
    private final LeaveTypeId leaveTypeId;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;

    private BigDecimal entitlement;
    private BigDecimal adjustment;
    private BigDecimal consumed;
    private BigDecimal reserved;

    private LeaveBalance(LeaveBalanceId id, UUID subjectId, UUID contextId,
                         LeaveTypeId leaveTypeId, LocalDate periodStart, LocalDate periodEnd,
                         BigDecimal entitlement) {
        super(id);
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId must not be null");
        this.contextId = Objects.requireNonNull(contextId, "contextId must not be null");
        this.leaveTypeId = Objects.requireNonNull(leaveTypeId, "leaveTypeId must not be null");
        this.periodStart = Objects.requireNonNull(periodStart, "periodStart must not be null");
        this.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd must not be null");
        if (periodEnd.isBefore(periodStart)) throw new IllegalArgumentException("periodEnd cannot be before periodStart");
        this.entitlement = Objects.requireNonNull(entitlement, "entitlement must not be null");
        this.adjustment = BigDecimal.ZERO;
        this.consumed = BigDecimal.ZERO;
        this.reserved = BigDecimal.ZERO;
    }

    public static LeaveBalance allocate(LeaveBalanceId id, UUID subjectId, UUID contextId,
                                        LeaveTypeId leaveTypeId, LocalDate periodStart,
                                        LocalDate periodEnd, BigDecimal entitlement) {
        if (entitlement == null || entitlement.signum() < 0)
            throw new IllegalArgumentException("Entitlement cannot be negative");
        LeaveBalance b = new LeaveBalance(id, subjectId, contextId, leaveTypeId, periodStart, periodEnd, entitlement);
        b.raise(new LeaveBalanceAllocated(id, subjectId, contextId, leaveTypeId, periodStart, periodEnd, entitlement));
        return b;
    }

    public BigDecimal available() {
        return entitlement.add(adjustment).subtract(consumed).subtract(reserved);
    }

    public void adjust(BigDecimal amount, String reason) {
        Objects.requireNonNull(amount, "adjustment amount must not be null");
        this.adjustment = this.adjustment.add(amount);
        incrementVersion();
        updatedAt = Instant.now();
        raise(new LeaveBalanceAdjusted(getId(), amount, reason));
    }

    public void reserve(BigDecimal amount) {
        Objects.requireNonNull(amount, "reserve amount must not be null");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Reserved amount must be positive");
        if (available().compareTo(amount) < 0) throw new IllegalStateException("Insufficient leave balance to reserve");
        this.reserved = this.reserved.add(amount);
        incrementVersion();
        updatedAt = Instant.now();
    }

    public void releaseReservation(BigDecimal amount) {
        Objects.requireNonNull(amount, "release amount must not be null");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Release amount must be positive");
        if (this.reserved.compareTo(amount) < 0) throw new IllegalStateException("Cannot release more than reserved amount");
        this.reserved = this.reserved.subtract(amount);
        incrementVersion();
        updatedAt = Instant.now();
    }

    public void consume(BigDecimal amount) {
        Objects.requireNonNull(amount, "consume amount must not be null");
        if (amount.signum() <= 0) throw new IllegalArgumentException("Consumed amount must be positive");
        if (this.reserved.compareTo(amount) >= 0) {
            this.reserved = this.reserved.subtract(amount);
        } else {
            if (available().compareTo(amount) < 0) throw new IllegalStateException("Insufficient leave balance to consume");
        }
        this.consumed = this.consumed.add(amount);
        incrementVersion();
        updatedAt = Instant.now();
    }

    public UUID getSubjectId() { return subjectId; }
    public UUID getContextId() { return contextId; }
    public LeaveTypeId getLeaveTypeId() { return leaveTypeId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public BigDecimal getEntitlement() { return entitlement; }
    public BigDecimal getAdjustment() { return adjustment; }
    public BigDecimal getConsumed() { return consumed; }
    public BigDecimal getReserved() { return reserved; }
}
