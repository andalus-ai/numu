package tech.kayys.syirkah.accounting.application.audit;

import tech.kayys.syirkah.accounting.domain.audit.*;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InternalAuditService {

    private final Map<String, AuditableEntity> auditUniverse = new ConcurrentHashMap<>();
    private final Map<String, AuditEngagement> engagements = new ConcurrentHashMap<>();
    private final Map<String, List<AuditFinding>> findingsByEngagement = new ConcurrentHashMap<>();
    private final Map<String, WorkingPaper> workingPapers = new ConcurrentHashMap<>();
    private final Map<String, AuditPlan> auditPlans = new ConcurrentHashMap<>();
    private final Map<String, AuditRecommendation> recommendations = new ConcurrentHashMap<>();
    private final Map<String, AuditFollowUp> followUps = new ConcurrentHashMap<>();

    private final AuditUniverseScoringEngine scoringEngine = new AuditUniverseScoringEngine();
    private final AuditPlanBuilder planBuilder = new AuditPlanBuilder();

    // ── Audit Universe ────────────────────────────────────────────────────────

    public AuditableEntity registerAuditableEntity(String entityId, String name,
                                                   String processType, String riskRating) {
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("entityId must not be blank");
        }
        var entity = new AuditableEntity(entityId, name, processType, riskRating);
        auditUniverse.put(entityId, entity);
        return entity;
    }

    public AuditableEntity registerAuditableEntity(String entityId, String processType, String riskRating) {
        return registerAuditableEntity(entityId, entityId, processType, riskRating);
    }

    public void rateEntity(String entityId, int externalScore, int ncCount, int overdueCount, double materiality) {
        var entity = getAuditableEntity(entityId);
        var inputs = new AuditUniverseScoringEngine.Inputs(externalScore, ncCount, overdueCount, materiality);
        int score = scoringEngine.score(inputs);
        RiskRating rating = scoringEngine.rating(score);
        entity.rate(rating, score);
    }

    public List<AuditableEntity> getAuditUniverse() {
        return List.copyOf(auditUniverse.values());
    }

    public AuditableEntity getAuditableEntity(String entityId) {
        var entity = auditUniverse.get(entityId);
        if (entity == null) throw new IllegalArgumentException("AuditableEntity not found: " + entityId);
        return entity;
    }

    // ── Audit Planning ────────────────────────────────────────────────────────

    public AuditPlan createAuditPlan(String planId, String tenantId, String companyId, int planYear, String title) {
        var plan = new AuditPlan(AuditPlanId.of(planId), tenantId, companyId, planYear, title);
        auditPlans.put(planId, plan);
        return plan;
    }

    public void autoPopulatePlan(String planId, int hoursBudget, String leadAuditor) {
        var plan = getAuditPlan(planId);
        planBuilder.build(plan, getAuditUniverse(), hoursBudget, leadAuditor);
    }

    public void approveAuditPlan(String planId, String actor) {
        getAuditPlan(planId).approve(actor);
    }

    public void activateAuditPlan(String planId) {
        getAuditPlan(planId).activate();
    }

    public AuditPlan getAuditPlan(String planId) {
        var plan = auditPlans.get(planId);
        if (plan == null) throw new IllegalArgumentException("AuditPlan not found: " + planId);
        return plan;
    }

    // ── Engagement Lifecycle ──────────────────────────────────────────────────

    public AuditEngagement openEngagement(String entityId, String engagementId,
                                          String title, String leadAuditor) {
        if (!auditUniverse.containsKey(entityId)) {
            throw new IllegalStateException("Entity not in audit universe: " + entityId);
        }
        String id = (engagementId == null || engagementId.isBlank())
                ? UUID.randomUUID().toString()
                : engagementId;
        var engagement = new AuditEngagement(id, entityId, title, leadAuditor, LocalDate.now());
        engagements.put(id, engagement);
        findingsByEngagement.put(id, new ArrayList<>());
        return engagement;
    }

    public AuditEngagement advanceEngagement(String engagementId, AuditEngagement.Phase nextPhase) {
        var engagement = requireEngagement(engagementId);
        engagement.advancePhase(nextPhase);
        return engagement;
    }

    public AuditEngagement getEngagement(String engagementId) {
        return requireEngagement(engagementId);
    }

    public List<AuditEngagement> listEngagements() {
        return List.copyOf(engagements.values());
    }

    // ── Working Papers ────────────────────────────────────────────────────────

    public WorkingPaper attachWorkingPaper(String engagementId, String title, String reference,
                                           String preparedBy, String documentId) {
        requireEngagement(engagementId);
        var id = WorkingPaperId.newId();
        var wp = new WorkingPaper(id, EngagementId.of(engagementId), title, reference, preparedBy, documentId);
        workingPapers.put(id.value(), wp);
        return wp;
    }

    public void reviewWorkingPaper(String workingPaperId, String reviewer) {
        var wp = workingPapers.get(workingPaperId);
        if (wp == null) throw new IllegalArgumentException("WorkingPaper not found: " + workingPaperId);
        wp.review(reviewer);
    }

    public List<WorkingPaper> getWorkingPapers(String engagementId) {
        var eid = EngagementId.of(engagementId);
        return workingPapers.values().stream()
                .filter(w -> w.engagementId().equals(eid))
                .toList();
    }

    // ── Findings ─────────────────────────────────────────────────────────────

    public AuditFinding recordFinding(String engagementId, String title, String condition,
                                      AuditFinding.Severity severity) {
        var engagement = requireEngagement(engagementId);

        if (engagement.phase() == AuditEngagement.Phase.PLANNING
                || engagement.phase() == AuditEngagement.Phase.CLOSED) {
            throw new IllegalStateException(
                    "Cannot record findings in phase: " + engagement.phase());
        }

        var finding = new AuditFinding(UUID.randomUUID().toString(), engagementId,
                title, condition, severity);
        findingsByEngagement.get(engagementId).add(finding);
        return finding;
    }

    public AuditFinding recordFinding(String engagementId, String condition,
                                      AuditFinding.Severity severity, String recommendation) {
        return recordFinding(engagementId, condition, condition, severity);
    }

    public List<AuditFinding> getFindings(String engagementId) {
        requireEngagement(engagementId);
        return Collections.unmodifiableList(
                findingsByEngagement.getOrDefault(engagementId, Collections.emptyList()));
    }

    public List<AuditFinding> getFindingsBySeverity(AuditFinding.Severity severity) {
        return findingsByEngagement.values().stream()
                .flatMap(List::stream)
                .filter(f -> f.severity() == severity)
                .toList();
    }

    // ── Recommendations & Follow-Up ───────────────────────────────────────────

    public AuditRecommendation issueRecommendation(String findingId, String action, String ownerUserId, LocalDate dueDate) {
        var id = RecommendationId.newId();
        var rec = new AuditRecommendation(id, FindingId.of(findingId), action, ownerUserId, dueDate);
        recommendations.put(id.value(), rec);
        return rec;
    }

    public void acceptRecommendation(String recommendationId, String actor) {
        var rec = recommendations.get(recommendationId);
        if (rec == null) throw new IllegalArgumentException("Recommendation not found: " + recommendationId);
        rec.accept(actor);
    }

    public void markRecommendationImplemented(String recommendationId, String closureNotes) {
        var rec = recommendations.get(recommendationId);
        if (rec == null) throw new IllegalArgumentException("Recommendation not found: " + recommendationId);
        rec.markImplemented(closureNotes);
    }

    public AuditFollowUp scheduleFollowUp(String recommendationId, String reviewerUserId, LocalDate scheduledDate) {
        var id = FollowUpId.newId();
        var fu = new AuditFollowUp(id, RecommendationId.of(recommendationId), reviewerUserId, scheduledDate);
        followUps.put(id.value(), fu);
        return fu;
    }

    public void completeFollowUp(String followUpId, String outcomeNotes) {
        var fu = followUps.get(followUpId);
        if (fu == null) throw new IllegalArgumentException("FollowUp not found: " + followUpId);
        fu.complete(outcomeNotes);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuditEngagement requireEngagement(String engagementId) {
        var eng = engagements.get(engagementId);
        if (eng == null) throw new IllegalArgumentException("AuditEngagement not found: " + engagementId);
        return eng;
    }
}
