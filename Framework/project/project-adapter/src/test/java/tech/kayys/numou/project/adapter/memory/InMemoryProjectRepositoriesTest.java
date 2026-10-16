package tech.kayys.numou.project.adapter.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.project.domain.milestone.MilestoneType;
import tech.kayys.numou.project.domain.milestone.ProjectMilestone;
import tech.kayys.numou.project.domain.milestone.ProjectMilestoneId;
import tech.kayys.numou.project.domain.phase.PhaseType;
import tech.kayys.numou.project.domain.phase.ProjectPhase;
import tech.kayys.numou.project.domain.phase.ProjectPhaseId;
import tech.kayys.numou.project.domain.project.Project;
import tech.kayys.numou.project.domain.project.ProjectCostEntry;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.domain.project.ProjectNumber;
import tech.kayys.numou.project.domain.project.ProjectRevenueEntry;
import tech.kayys.numou.project.domain.project.ProjectType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("In-memory project adapters")
class InMemoryProjectRepositoriesTest {

    private final InMemoryProjectRepository projects =
            new InMemoryProjectRepository();

    private static Project project(String number) {
        return Project.create(
                ProjectId.generate(),
                ProjectNumber.of(number),
                "Warehouse Expansion",
                ProjectType.CAPITAL_CIP,
                null
        );
    }

    private static ProjectPhase phase(ProjectId projectId, int sequence, String name) {
        return ProjectPhase.create(
                ProjectPhaseId.generate(),
                projectId,
                sequence,
                name,
                PhaseType.EXECUTION
        );
    }

    private static ProjectMilestone milestone(
            ProjectId projectId,
            int sequence,
            String name,
            LocalDate plannedDate
    ) {
        return ProjectMilestone.create(
                ProjectMilestoneId.generate(),
                projectId,
                sequence,
                name,
                MilestoneType.DELIVERABLE,
                plannedDate
        );
    }

    private static <T> T await(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    @Test
    @DisplayName("projects are stored, indexed by number and deletable")
    void projectLifecycleInStore() {
        var project = project("PRJ-601");

        await(projects.save(project));

        assertTrue(await(projects.existsById(project.id())));
        assertEquals(project, await(projects.findById(project.id())).orElseThrow());
        assertEquals(
                project,
                await(projects.findByNumber(ProjectNumber.of("PRJ-601"))).orElseThrow()
        );

        await(projects.deleteById(project.id()));

        assertFalse(await(projects.existsById(project.id())));
        assertTrue(await(projects.findByNumber(ProjectNumber.of("PRJ-601"))).isEmpty());
    }

    @Test
    @DisplayName("a deleted project is also removed from the number index")
    void deleteKeepsNumberIndexConsistent() {
        var project = project("PRJ-602");
        await(projects.save(project));

        await(projects.delete(project));

        assertTrue(await(projects.findById(project.id())).isEmpty());
        assertTrue(await(projects.findByNumber(ProjectNumber.of("PRJ-602"))).isEmpty());
    }

    @Test
    @DisplayName("phases are found per project in sequence order")
    void phasesAreFoundPerProject() {
        var phases = new InMemoryProjectPhaseRepository();
        var first = project("PRJ-603");
        var second = project("PRJ-604");

        await(phases.save(phase(first.id(), 2, "Installation")));
        await(phases.save(phase(first.id(), 1, "Foundation")));
        await(phases.save(phase(second.id(), 1, "Other project phase")));

        var found = await(phases.findByProjectId(first.id()));

        assertEquals(
                List.of("Foundation", "Installation"),
                found.stream().map(ProjectPhase::name).toList()
        );
    }

    @Test
    @DisplayName("milestones are found per project in sequence order")
    void milestonesAreFoundPerProject() {
        var milestones = new InMemoryProjectMilestoneRepository();
        var project = project("PRJ-605");

        await(milestones.save(milestone(
                project.id(),
                2,
                "Electrical Energized",
                LocalDate.of(2026, 8, 31)
        )));
        await(milestones.save(milestone(
                project.id(),
                1,
                "Foundation Complete",
                LocalDate.of(2026, 5, 31)
        )));

        var found = await(milestones.findByProjectId(project.id()));

        assertEquals(
                List.of("Foundation Complete", "Electrical Energized"),
                found.stream().map(ProjectMilestone::name).toList()
        );
    }

    @Test
    @DisplayName("cost and revenue entries are appended per project")
    void financeEntriesAreScopedPerProject() {
        var store = new InMemoryProjectFinanceStore();
        var project = project("PRJ-606");
        var other = project("PRJ-607");

        store.saveCost(ProjectCostEntry.of(project.id(), "LABOR", new BigDecimal("1000"), "USD", "TS-1"));
        store.saveCost(ProjectCostEntry.of(other.id(), "LABOR", new BigDecimal("2000"), "USD", "TS-2"));
        store.saveRevenue(ProjectRevenueEntry.of(project.id(), "MILESTONE_1", new BigDecimal("5000"), "USD", "INV-1"));

        assertEquals(1, store.findCosts(project.id()).size());
        assertEquals(new BigDecimal("1000"), store.findCosts(project.id()).getFirst().amount());
        assertEquals(1, store.findRevenues(project.id()).size());
        assertTrue(store.findRevenues(other.id()).isEmpty());
    }
}
