package tech.kayys.numou.planning.application;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.planning.application.port.WorkBreakdownActivityLinkRepository;
import tech.kayys.numou.planning.application.port.WorkBreakdownRepository;
import tech.kayys.numou.planning.domain.*;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LinkScheduleActivityToWorkBreakdownNodeTest {
    @Test
    void linksOnlyActivityAndNodeThatBelongToSuppliedPlanAndTenantStructure() {
        UUID tenant = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        WorkBreakdownStructure structure = WorkBreakdownStructure.create(UUID.randomUUID(), "WBS", List.of(
                new WorkBreakdownNode(root, null, "ROOT", "Root", 0),
                new WorkBreakdownNode(child, root, "TASK", "Task", 0)));
        SchedulePlan plan = SchedulePlan.create("PLAN-" + UUID.randomUUID(), "Plan");
        ScheduleActivity activity = ScheduleActivity.create("A1", "Activity",
                LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-02"), null, 0);
        plan.addActivity(activity);
        List<WorkBreakdownActivityLink> saved = new ArrayList<>();
        WorkBreakdownRepository structureRepository = new WorkBreakdownRepository() {
            public void create(UUID tenantId, WorkBreakdownStructure value) { throw new UnsupportedOperationException(); }
            public Optional<WorkBreakdownStructure> findById(UUID tenantId, UUID structureId) {
                return tenant.equals(tenantId) && structure.id().equals(structureId) ? Optional.of(structure) : Optional.empty();
            }
        };
        WorkBreakdownActivityLinkRepository linkRepository = new WorkBreakdownActivityLinkRepository() {
            public void create(WorkBreakdownActivityLink link) { saved.add(link); }
            public List<WorkBreakdownActivityLink> findByStructure(UUID tenantId, UUID structureId) { return List.of(); }
            public List<WorkBreakdownActivityLink> findByPlan(UUID tenantId, UUID planId) { return List.of(); }
        };

        var useCase = new LinkScheduleActivityToWorkBreakdownNode(structureRepository, linkRepository);
        var link = useCase.execute(tenant, plan, structure.id(), child, activity.id());

        assertEquals(1, saved.size());
        assertEquals(child, link.nodeId());
        assertEquals(plan.id(), link.planId());
        assertEquals(activity.id(), link.activityId());
    }

    @Test
    void rejectsUnknownActivityAndCrossTenantStructureLookup() {
        UUID tenant = UUID.randomUUID();
        UUID root = UUID.randomUUID();
        WorkBreakdownStructure structure = WorkBreakdownStructure.create(UUID.randomUUID(), "WBS", List.of(
                new WorkBreakdownNode(root, null, "ROOT", "Root", 0)));
        WorkBreakdownRepository repository = new WorkBreakdownRepository() {
            public void create(UUID tenantId, WorkBreakdownStructure value) {}
            public Optional<WorkBreakdownStructure> findById(UUID tenantId, UUID structureId) {
                return tenant.equals(tenantId) ? Optional.of(structure) : Optional.empty();
            }
        };
        WorkBreakdownActivityLinkRepository links = new WorkBreakdownActivityLinkRepository() {
            public void create(WorkBreakdownActivityLink link) { fail("must not persist invalid link"); }
            public List<WorkBreakdownActivityLink> findByStructure(UUID tenantId, UUID structureId) { return List.of(); }
            public List<WorkBreakdownActivityLink> findByPlan(UUID tenantId, UUID planId) { return List.of(); }
        };
        SchedulePlan plan = SchedulePlan.create("PLAN-" + UUID.randomUUID(), "Plan");
        var useCase = new LinkScheduleActivityToWorkBreakdownNode(repository, links);

        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(tenant, plan, structure.id(), root, ActivityId.generate()));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute(UUID.randomUUID(), plan, structure.id(), root, ActivityId.generate()));
    }
}
