package tech.kayys.numou.planning.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.application.port.WorkBreakdownActivityLinkRepository;
import tech.kayys.numou.planning.application.port.WorkBreakdownRepository;
import tech.kayys.numou.planning.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CalculateWorkBreakdownProgressTest {
    private static final LocalDate AS_OF = LocalDate.parse("2026-10-23");

    @Test
    void aggregatesOnlyLeafLinkedActivitiesUsingExplicitWeights() {
        UUID tenant = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        UUID leafA = UUID.randomUUID();
        UUID leafB = UUID.randomUUID();
        WorkBreakdownStructure wbs = WorkBreakdownStructure.create(UUID.randomUUID(), "WBS", List.of(
                new WorkBreakdownNode(root, null, "ROOT", "Root", 0),
                new WorkBreakdownNode(parent, root, "GROUP", "Group", 0),
                new WorkBreakdownNode(leafA, parent, "A", "Leaf A", 0),
                new WorkBreakdownNode(leafB, parent, "B", "Leaf B", 1)));
        SchedulePlan plan = SchedulePlan.create("PLAN-" + UUID.randomUUID(), "Plan");
        ScheduleActivity parentActivity = activity("PARENT", "Parent activity");
        ScheduleActivity a = activity("A", "Leaf activity A");
        ScheduleActivity b = activity("B", "Leaf activity B");
        plan.addActivity(parentActivity);
        plan.addActivity(a);
        plan.addActivity(b);

        List<WorkBreakdownActivityLink> linkRows = List.of(
                link(tenant, wbs, root, plan, parentActivity),
                link(tenant, wbs, leafA, plan, a),
                link(tenant, wbs, leafB, plan, b));
        var useCase = useCase(tenant, wbs, linkRows);
        Map<ActivityId, ProgressAssessment> assessments = Map.of(
                parentActivity.id(), assessment(parentActivity, "100"),
                a.id(), assessment(a, "50"),
                b.id(), assessment(b, "100"));
        Map<ActivityId, BigDecimal> weights = Map.of(
                parentActivity.id(), new BigDecimal("1000"),
                a.id(), new BigDecimal("1"),
                b.id(), new BigDecimal("3"));

        ProgressRollup result = useCase.execute(tenant, wbs.id(), parent, plan, AS_OF, assessments, weights);

        assertEquals(new BigDecimal("87.50"), result.completionPercent());
        assertEquals(2, result.includedActivityCount());
        assertEquals(new BigDecimal("4"), result.totalWeight());
        assertEquals(tenant, result.tenantId());
        assertEquals(AS_OF, result.asOfDate());
    }

    @Test
    void rejectsMissingAssessmentAndMismatchedAsOfDate() {
        UUID tenant = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        WorkBreakdownStructure wbs = WorkBreakdownStructure.create(UUID.randomUUID(), "WBS", List.of(
                new WorkBreakdownNode(root, null, "ROOT", "Root", 0)));
        SchedulePlan plan = SchedulePlan.create("PLAN-" + UUID.randomUUID(), "Plan");
        ScheduleActivity activity = activity("A", "Activity");
        plan.addActivity(activity);
        var useCase = useCase(tenant, wbs, List.of(link(tenant, wbs, root, plan, activity)));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(
                tenant, wbs.id(), root, plan, AS_OF, Map.of(), Map.of(activity.id(), BigDecimal.ONE)));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(
                tenant, wbs.id(), root, plan, AS_OF,
                Map.of(activity.id(), assessment(activity, "50", AS_OF.minusDays(1))),
                Map.of(activity.id(), BigDecimal.ONE)));
    }

    @Test
    void rejectsNonPositiveWeights() {
        UUID tenant = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        WorkBreakdownStructure wbs = WorkBreakdownStructure.create(UUID.randomUUID(), "WBS", List.of(
                new WorkBreakdownNode(root, null, "ROOT", "Root", 0)));
        SchedulePlan plan = SchedulePlan.create("PLAN-" + UUID.randomUUID(), "Plan");
        ScheduleActivity activity = activity("A", "Activity");
        plan.addActivity(activity);
        var useCase = useCase(tenant, wbs, List.of(link(tenant, wbs, root, plan, activity)));
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(
                tenant, wbs.id(), root, plan, AS_OF,
                Map.of(activity.id(), assessment(activity, "50")), Map.of(activity.id(), BigDecimal.ZERO)));
    }

    private static ScheduleActivity activity(String code, String name) {
        return ScheduleActivity.create(code, name, AS_OF.minusDays(2), AS_OF.plusDays(2), null, 0);
    }

    private static ProgressAssessment assessment(ScheduleActivity activity, String actual) {
        return assessment(activity, actual, AS_OF);
    }

    private static ProgressAssessment assessment(ScheduleActivity activity, String actual, LocalDate date) {
        return ProgressAssessment.assess(activity, date, new BigDecimal(actual));
    }

    private static WorkBreakdownActivityLink link(UUID tenant, WorkBreakdownStructure wbs,
            UUID node, SchedulePlan plan, ScheduleActivity activity) {
        return new WorkBreakdownActivityLink(tenant, wbs.id(), node, plan.id(), activity.id());
    }

    private static CalculateWorkBreakdownProgress useCase(UUID tenant, WorkBreakdownStructure wbs,
            List<WorkBreakdownActivityLink> rows) {
        WorkBreakdownRepository structures = new WorkBreakdownRepository() {
            public void create(UUID tenantId, WorkBreakdownStructure structure) {}
            public Optional<WorkBreakdownStructure> findById(UUID tenantId, UUID structureId) {
                return tenant.equals(tenantId) && wbs.id().equals(structureId)
                        ? Optional.of(wbs) : Optional.empty();
            }
        };
        WorkBreakdownActivityLinkRepository links = new WorkBreakdownActivityLinkRepository() {
            public void create(WorkBreakdownActivityLink link) {}
            public List<WorkBreakdownActivityLink> findByStructure(UUID tenantId, UUID structureId) {
                return rows;
            }
            public List<WorkBreakdownActivityLink> findByPlan(UUID tenantId, UUID planId) { return List.of(); }
        };
        return new CalculateWorkBreakdownProgress(structures, links);
    }
}
