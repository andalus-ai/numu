package tech.kayys.numou.planning.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable schedule-change governance aggregate. State transitions return a new aggregate;
 * callers persist the returned value using optimistic concurrency at the application boundary.
 */
public record ScheduleChangeRequest(UUID requestId, UUID tenantId, UUID structureId, PlanId planId,
        long baseRevision, long targetRevision, UUID requestedBy, Instant requestedAt, String reason,
        Status status, PlanChangeImpactReport impactReport, List<ScheduleChangeDecision> decisions) {
    public ScheduleChangeRequest {
        Objects.requireNonNull(requestId, "Request id cannot be null");
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(planId, "Plan id cannot be null");
        Objects.requireNonNull(requestedBy, "Requester cannot be null");
        Objects.requireNonNull(requestedAt, "Request timestamp cannot be null");
        reason = Objects.requireNonNull(reason, "Reason cannot be null").trim();
        Objects.requireNonNull(status, "Status cannot be null");
        decisions = List.copyOf(Objects.requireNonNull(decisions, "Decisions cannot be null"));
        if (reason.isEmpty()) throw new IllegalArgumentException("Change reason cannot be blank");
        if (baseRevision < 0 || targetRevision <= baseRevision)
            throw new IllegalArgumentException("Target revision must be greater than non-negative base revision");
        if (status == Status.DRAFT && impactReport != null)
            throw new IllegalArgumentException("Draft request cannot carry an approval impact report");
        if (status != Status.DRAFT && status != Status.SUPERSEDED && impactReport == null)
            throw new IllegalArgumentException("Submitted or decided request must preserve its impact report");
        if (impactReport != null) validateImpactScope(tenantId, structureId, planId, baseRevision, targetRevision, impactReport);
        for (ScheduleChangeDecision decision : decisions) Objects.requireNonNull(decision, "Decision cannot be null");
    }

    public static ScheduleChangeRequest draft(UUID requestId, UUID tenantId, UUID structureId, PlanId planId,
            long baseRevision, long targetRevision, UUID requestedBy, Instant requestedAt, String reason) {
        return new ScheduleChangeRequest(requestId, tenantId, structureId, planId, baseRevision, targetRevision,
                requestedBy, requestedAt, reason, Status.DRAFT, null, List.of());
    }

    public ScheduleChangeRequest submit(PlanChangeImpactReport report) {
        if (status != Status.DRAFT) throw new IllegalStateException("Only a draft request can be submitted");
        Objects.requireNonNull(report, "Impact report cannot be null");
        validateImpactScope(tenantId, structureId, planId, baseRevision, targetRevision, report);
        if (report.currentCapturedAt().isBefore(requestedAt))
            throw new IllegalArgumentException("Impact evidence predates the change request");
        return new ScheduleChangeRequest(requestId, tenantId, structureId, planId, baseRevision, targetRevision,
                requestedBy, requestedAt, reason, Status.SUBMITTED, report, decisions);
    }

    public ScheduleChangeRequest decide(UUID decisionId, UUID actorId, ScheduleChangeDecision.Outcome outcome,
            Instant decidedAt, String rationale, long livePlanRevision) {
        Objects.requireNonNull(decisionId, "Decision id cannot be null");
        Objects.requireNonNull(actorId, "Decision actor cannot be null");
        Objects.requireNonNull(outcome, "Decision outcome cannot be null");
        Objects.requireNonNull(decidedAt, "Decision timestamp cannot be null");
        if (status != Status.SUBMITTED) {
            ScheduleChangeDecision prior = decisions.stream().filter(d -> d.decisionId().equals(decisionId))
                    .findFirst().orElse(null);
            if (prior != null && prior.actorId().equals(actorId) && prior.outcome() == outcome
                    && prior.livePlanRevision() == livePlanRevision
                    && prior.rationale().equals(Objects.requireNonNull(rationale).trim())
                    && prior.decidedAt().equals(decidedAt)) return this;
            throw new IllegalStateException("Only a submitted request can receive a decision");
        }
        if (decidedAt.isBefore(impactReport.currentCapturedAt()))
            throw new IllegalArgumentException("Decision timestamp cannot precede impact evidence");
        if (decisions.stream().anyMatch(d -> d.decisionId().equals(decisionId)))
            throw new IllegalArgumentException("Decision id already exists");
        if (decisions.stream().anyMatch(d -> d.outcome() == ScheduleChangeDecision.Outcome.APPROVE
                || d.outcome() == ScheduleChangeDecision.Outcome.REJECT))
            throw new IllegalStateException("A terminal decision already exists");
        if (outcome == ScheduleChangeDecision.Outcome.APPROVE || outcome == ScheduleChangeDecision.Outcome.REJECT) {
            if (actorId.equals(requestedBy))
                throw new IllegalArgumentException("Requester cannot approve or reject their own change");
            if (livePlanRevision != baseRevision)
                throw new IllegalStateException("Impact report is stale: live plan revision differs from its base revision");
        } else if (outcome == ScheduleChangeDecision.Outcome.MARK_STALE) {
            if (livePlanRevision == baseRevision)
                throw new IllegalArgumentException("Cannot mark impact evidence stale while base revision is still current");
        } else {
            throw new IllegalArgumentException("Use supersede() to supersede a request");
        }
        ScheduleChangeDecision decision = new ScheduleChangeDecision(decisionId, actorId, outcome,
                decidedAt, rationale, livePlanRevision);
        List<ScheduleChangeDecision> history = new ArrayList<>(decisions);
        history.add(decision);
        Status next = switch (outcome) {
            case APPROVE -> Status.APPROVED;
            case REJECT -> Status.REJECTED;
            case MARK_STALE -> Status.STALE;
            case SUPERSEDE -> throw new IllegalArgumentException("Use supersede() to supersede a request");
        };
        return new ScheduleChangeRequest(requestId, tenantId, structureId, planId, baseRevision, targetRevision,
                requestedBy, requestedAt, reason, next, impactReport, history);
    }

    public ScheduleChangeRequest supersede(UUID decisionId, UUID actorId, Instant at, String rationale,
            long livePlanRevision) {
        if (status != Status.SUBMITTED && status != Status.STALE)
            throw new IllegalStateException("Only submitted or stale requests can be superseded");
        if (livePlanRevision < baseRevision)
            throw new IllegalArgumentException("Live plan revision cannot precede request base revision");
        ScheduleChangeDecision decision = new ScheduleChangeDecision(decisionId, actorId,
                ScheduleChangeDecision.Outcome.SUPERSEDE, at, rationale, livePlanRevision);
        List<ScheduleChangeDecision> history = new ArrayList<>(decisions);
        history.add(decision);
        return new ScheduleChangeRequest(requestId, tenantId, structureId, planId, baseRevision, targetRevision,
                requestedBy, requestedAt, reason, Status.SUPERSEDED, impactReport, history);
    }

    private static void validateImpactScope(UUID tenantId, UUID structureId, PlanId planId,
            long baseRevision, long targetRevision, PlanChangeImpactReport report) {
        if (!tenantId.equals(report.tenantId()) || !structureId.equals(report.structureId())
                || !planId.equals(report.planId()))
            throw new IllegalArgumentException("Impact report belongs to a different tenant, structure, or plan");
        if (report.previousRevision() != baseRevision || report.currentRevision() != targetRevision)
            throw new IllegalArgumentException("Impact report revisions do not match the request");
    }

    public enum Status { DRAFT, SUBMITTED, APPROVED, REJECTED, STALE, SUPERSEDED }
}
