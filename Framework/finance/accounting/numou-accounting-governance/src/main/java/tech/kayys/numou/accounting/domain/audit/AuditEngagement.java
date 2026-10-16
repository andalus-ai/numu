package tech.kayys.syirkah.accounting.domain.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class AuditEngagement {
    public enum Phase { PLANNING, FIELDWORK, REVIEW, REPORTING, FOLLOW_UP, CLOSED }

    private final String engagementId;
    private final String planId;
    private final String title;
    private final String leadAuditor;
    private Phase phase;
    private final LocalDate startDate;
    private LocalDate endDate;
    private EngagementStatus status;
    private final List<EngagementPhaseRecord> phaseRecords = new ArrayList<>();

    public AuditEngagement(String engagementId, String planId, String title, String leadAuditor, LocalDate startDate) {
        this.engagementId = Objects.requireNonNull(engagementId, "engagementId must not be null");
        this.planId = Objects.requireNonNull(planId, "planId must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.leadAuditor = Objects.requireNonNull(leadAuditor, "leadAuditor must not be null");
        this.startDate = Objects.requireNonNull(startDate, "startDate must not be null");
        this.phase = Phase.PLANNING;
        this.status = EngagementStatus.OPEN;
        this.phaseRecords.add(new EngagementPhaseRecord(Phase.PLANNING, Instant.now(), null, leadAuditor));
    }

    public void advancePhase(Phase newPhase) {
        Objects.requireNonNull(newPhase, "newPhase must not be null");
        if (this.phase == Phase.CLOSED) {
            throw new IllegalStateException("Cannot advance a CLOSED engagement");
        }
        // Close last phase record
        if (!phaseRecords.isEmpty()) {
            var last = phaseRecords.get(phaseRecords.size() - 1);
            phaseRecords.set(phaseRecords.size() - 1, new EngagementPhaseRecord(
                    last.phase(), last.startedAt(), Instant.now(), last.actor()));
        }
        this.phase = newPhase;
        this.phaseRecords.add(new EngagementPhaseRecord(newPhase, Instant.now(), null, leadAuditor));
        this.status = switch (newPhase) {
            case PLANNING -> EngagementStatus.OPEN;
            case FIELDWORK -> EngagementStatus.IN_FIELDWORK;
            case REVIEW -> EngagementStatus.IN_REVIEW;
            case REPORTING -> EngagementStatus.REPORTING;
            case FOLLOW_UP, CLOSED -> EngagementStatus.CLOSED;
        };
    }

    public void close(String actor, String notes) {
        advancePhase(Phase.CLOSED);
        this.endDate = LocalDate.now();
    }

    public String engagementId() { return engagementId; }
    public EngagementId id() { return EngagementId.of(engagementId); }
    public String planId() { return planId; }
    public String entityId() { return planId; }
    public String title() { return title; }
    public String leadAuditor() { return leadAuditor; }
    public String auditor() { return leadAuditor; }
    public Phase phase() { return phase; }
    public Phase currentPhase() { return phase; }
    public EngagementStatus status() { return status; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public List<EngagementPhaseRecord> phaseRecords() { return List.copyOf(phaseRecords); }
}
