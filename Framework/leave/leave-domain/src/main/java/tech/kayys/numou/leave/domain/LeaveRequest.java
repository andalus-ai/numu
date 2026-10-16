package tech.kayys.numou.leave.domain;

import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.leave.domain.event.LeaveRequestApproved;
import tech.kayys.numou.leave.domain.event.LeaveRequestCancelled;
import tech.kayys.numou.leave.domain.event.LeaveRequestCreated;
import tech.kayys.numou.leave.domain.event.LeaveRequestRejected;
import tech.kayys.numou.leave.domain.event.LeaveRequestSubmitted;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public final class LeaveRequest extends AbstractAggregateRoot<LeaveRequestId> {

    private final UUID subjectId;
    private final UUID contextId;
    private final LeaveTypeId leaveTypeId;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal requestedAmount;
    private LeaveRequestStatus status;
    private String reason;
    private String approvedBy;
    private String rejectionReason;

    private LeaveRequest(LeaveRequestId id, UUID subjectId, UUID contextId,
                         LeaveTypeId leaveTypeId, LocalDate startDate, LocalDate endDate,
                         BigDecimal requestedAmount, String reason) {
        super(id);
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId must not be null");
        this.contextId = Objects.requireNonNull(contextId, "contextId must not be null");
        this.leaveTypeId = Objects.requireNonNull(leaveTypeId, "leaveTypeId must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.endDate = Objects.requireNonNull(endDate, "endDate must not be null");
        if (endDate.isBefore(startDate)) throw new IllegalArgumentException("Leave end date cannot be before start date");
        this.requestedAmount = Objects.requireNonNull(requestedAmount, "requestedAmount must not be null");
        if (requestedAmount.signum() <= 0) throw new IllegalArgumentException("Requested leave amount must be positive");
        this.reason = reason;
        this.status = LeaveRequestStatus.DRAFT;
    }

    public static LeaveRequest create(LeaveRequestId id, UUID subjectId, UUID contextId,
                                      LeaveTypeId leaveTypeId, LocalDate startDate,
                                      LocalDate endDate, BigDecimal requestedAmount, String reason) {
        LeaveRequest r = new LeaveRequest(id, subjectId, contextId, leaveTypeId, startDate, endDate, requestedAmount, reason);
        r.raise(new LeaveRequestCreated(id, subjectId, contextId, leaveTypeId, startDate, endDate, requestedAmount));
        return r;
    }

    public void submit() {
        if (status != LeaveRequestStatus.DRAFT) throw new IllegalStateException("Only draft leave requests can be submitted");
        this.status = LeaveRequestStatus.SUBMITTED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new LeaveRequestSubmitted(getId(), subjectId));
    }

    public void approve(String approver) {
        if (status != LeaveRequestStatus.SUBMITTED) throw new IllegalStateException("Only submitted leave requests can be approved");
        this.status = LeaveRequestStatus.APPROVED;
        this.approvedBy = approver;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new LeaveRequestApproved(getId(), subjectId, approver));
    }

    public void reject(String rejectedBy, String reason) {
        if (status != LeaveRequestStatus.SUBMITTED) throw new IllegalStateException("Only submitted leave requests can be rejected");
        this.status = LeaveRequestStatus.REJECTED;
        this.rejectionReason = reason;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new LeaveRequestRejected(getId(), subjectId, rejectedBy, reason));
    }

    public void cancel(String cancelReason) {
        if (status != LeaveRequestStatus.DRAFT
                && status != LeaveRequestStatus.SUBMITTED
                && status != LeaveRequestStatus.APPROVED) {
            throw new IllegalStateException("Leave request cannot be cancelled in status: " + status);
        }
        this.status = LeaveRequestStatus.CANCELLED;
        incrementVersion();
        updatedAt = Instant.now();
        raise(new LeaveRequestCancelled(getId(), subjectId, cancelReason));
    }

    public UUID getSubjectId() { return subjectId; }
    public UUID getContextId() { return contextId; }
    public LeaveTypeId getLeaveTypeId() { return leaveTypeId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public LeaveRequestStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public String getApprovedBy() { return approvedBy; }
    public String getRejectionReason() { return rejectionReason; }
}
