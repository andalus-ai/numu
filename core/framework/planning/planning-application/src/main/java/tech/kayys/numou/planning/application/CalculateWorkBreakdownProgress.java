package tech.kayys.numou.planning.application;

import tech.kayys.numou.planning.application.port.WorkBreakdownActivityLinkRepository;
import tech.kayys.numou.planning.application.port.WorkBreakdownRepository;
import tech.kayys.numou.planning.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates activity progress for a WBS node using an explicit weight policy.
 * Only activities linked to leaf nodes within the selected subtree contribute;
 * parent-linked activities are intentionally excluded to prevent ancestor /
 * descendant double counting.
 */
public final class CalculateWorkBreakdownProgress {
    private final WorkBreakdownRepository structures;
    private final WorkBreakdownActivityLinkRepository links;

    public CalculateWorkBreakdownProgress(
            WorkBreakdownRepository structures, WorkBreakdownActivityLinkRepository links) {
        this.structures = Objects.requireNonNull(structures, "WBS repository cannot be null");
        this.links = Objects.requireNonNull(links, "WBS link repository cannot be null");
    }

    /**
     * Assessments and weights must be supplied by an authorized caller and use
     * the same as-of date. Weights are explicit and must be comparable; this
     * use case does not infer weights from quantities or domain-specific data.
     */
    public ProgressRollup execute(
            UUID tenantId,
            UUID structureId,
            UUID targetNodeId,
            SchedulePlan authorizedPlan,
            LocalDate asOfDate,
            Map<ActivityId, ProgressAssessment> assessments,
            Map<ActivityId, BigDecimal> approvedWeights) {
        Objects.requireNonNull(tenantId, "Tenant id cannot be null");
        Objects.requireNonNull(structureId, "Structure id cannot be null");
        Objects.requireNonNull(targetNodeId, "Target node id cannot be null");
        Objects.requireNonNull(authorizedPlan, "Authorized plan cannot be null");
        Objects.requireNonNull(asOfDate, "As-of date cannot be null");
        Objects.requireNonNull(assessments, "Assessments cannot be null");
        Objects.requireNonNull(approvedWeights, "Approved weights cannot be null");

        WorkBreakdownStructure structure = structures.findById(tenantId, structureId)
                .orElseThrow(() -> new IllegalArgumentException("WBS structure not found for tenant"));
        structure.node(targetNodeId);

        Set<UUID> subtreeIds = new HashSet<>();
        subtreeIds.add(targetNodeId);
        structure.descendantsOf(targetNodeId).forEach(node -> subtreeIds.add(node.id()));
        Set<UUID> leafIds = subtreeIds.stream()
                .filter(id -> structure.childrenOf(id).isEmpty())
                .collect(Collectors.toUnmodifiableSet());

        Map<ActivityId, ScheduleActivity> planActivities = authorizedPlan.activities().stream()
                .collect(Collectors.toMap(ScheduleActivity::id, activity -> activity));
        List<WorkBreakdownActivityLink> structureLinks = links.findByStructure(tenantId, structureId);
        List<ProgressContribution> contributions = new ArrayList<>();
        Set<ActivityId> included = new HashSet<>();

        for (WorkBreakdownActivityLink link : structureLinks) {
            if (!tenantId.equals(link.tenantId()) || !structureId.equals(link.structureId())) {
                throw new IllegalStateException("Repository returned a link outside the requested tenant or structure");
            }
            if (!link.planId().equals(authorizedPlan.id())) continue;
            if (!leafIds.contains(link.nodeId())) continue;

            ActivityId activityId = link.activityId();
            if (!included.add(activityId)) {
                throw new IllegalStateException("Activity is linked more than once in the selected WBS scope");
            }
            if (!planActivities.containsKey(activityId)) {
                throw new IllegalArgumentException("WBS link references an activity outside the supplied plan");
            }
            ProgressAssessment assessment = assessments.get(activityId);
            if (assessment == null) {
                throw new IllegalArgumentException("Missing progress assessment for linked leaf activity");
            }
            if (!activityId.equals(assessment.activityId()) || !asOfDate.equals(assessment.asOfDate())) {
                throw new IllegalArgumentException("Assessment activity and as-of date must match rollup request");
            }
            BigDecimal weight = approvedWeights.get(activityId);
            if (weight == null || weight.signum() <= 0) {
                throw new IllegalArgumentException("A positive approved weight is required for each included activity");
            }
            contributions.add(ProgressContribution.from(UUID.randomUUID(), tenantId, assessment, weight));
        }

        if (contributions.isEmpty()) {
            throw new IllegalArgumentException("No assessed activities are linked to eligible WBS leaf nodes in this scope");
        }
        return ProgressRollup.aggregate(contributions);
    }
}
