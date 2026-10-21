package tech.kayys.numou.education.pesantren.domain;

import tech.kayys.numou.education.pesantren.domain.event.LeavePermitDecided;
import tech.kayys.numou.education.pesantren.domain.event.LeavePermitRequested;
import tech.kayys.numou.education.pesantren.domain.event.SantriReturned;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Izin pulang: a santri's request to leave the pesantren for a period, decided by an authorised person. */
public final class LeavePermit extends AbstractAggregateRoot<LeavePermitId> {

    private final TenantId tenantId;
    private final SantriId santriId;
    private final LocalDate from;
    private final LocalDate to;
    private final String reason;
    private LeavePermitStatus status = LeavePermitStatus.REQUESTED;
    private String decidedBy;
    private String decisionNote;
    private LocalDate returnedOn;

    private LeavePermit(LeavePermitId id, TenantId tenantId, SantriId santriId, LocalDate from, LocalDate to, String reason) {
        super(id);
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.santriId = Objects.requireNonNull(santriId, "santriId must not be null");
        this.from = Objects.requireNonNull(from, "from must not be null");
        this.to = Objects.requireNonNull(to, "to must not be null");
        if (to.isBefore(from)) throw new IllegalArgumentException("'to' cannot be before 'from'");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("reason must not be blank");
        this.reason = reason.trim();
    }

    public static LeavePermit request(LeavePermitId id, TenantId tenantId, SantriId santriId, LocalDate from,
                                      LocalDate to, String reason) {
        LeavePermit p = new LeavePermit(id, tenantId, santriId, from, to, reason);
        p.raise(new LeavePermitRequested(id, tenantId, santriId, from, to));
        return p;
    }

    public void approve(String approver) {
        decide(LeavePermitStatus.APPROVED, approver, null, true);
    }

    public void reject(String approver, String note) {
        decide(LeavePermitStatus.REJECTED, approver, note, false);
    }

    private void decide(LeavePermitStatus next, String by, String note, boolean approved) {
        if (status != LeavePermitStatus.REQUESTED) throw new IllegalStateException("Permit already decided: " + status);
        if (by == null || by.isBlank()) throw new IllegalArgumentException("A decision must name who made it");
        status = next;
        decidedBy = by.trim();
        decisionNote = note;
        touch();
        raise(new LeavePermitDecided(getId(), tenantId, approved));
    }

    /** Records the santri's return and returns how many days after the permitted date it happened (0 if on time). */
    public int markReturned(LocalDate date) {
        if (status != LeavePermitStatus.APPROVED) throw new IllegalStateException("Only APPROVED permits can be closed");
        if (date.isBefore(from)) throw new IllegalArgumentException("Return date is before the leave started");
        status = LeavePermitStatus.RETURNED;
        returnedOn = date;
        int late = (int) Math.max(0, ChronoUnit.DAYS.between(to, date));
        touch();
        raise(new SantriReturned(getId(), tenantId, santriId, late));
        return late;
    }

    /** Approved, not yet returned and past the permitted date. */
    public boolean isOverdue(LocalDate today) {
        return status == LeavePermitStatus.APPROVED && today.isAfter(to);
    }

    private void touch() {
        incrementVersion();
        updatedAt = Instant.now();
    }

    public TenantId getTenantId() { return tenantId; }
    public SantriId getSantriId() { return santriId; }
    public LocalDate getFrom() { return from; }
    public LocalDate getTo() { return to; }
    public String getReason() { return reason; }
    public LeavePermitStatus getStatus() { return status; }
    public String getDecidedBy() { return decidedBy; }
    public String getDecisionNote() { return decisionNote; }
    public LocalDate getReturnedOn() { return returnedOn; }
}
