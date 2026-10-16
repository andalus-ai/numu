package tech.kayys.syirkah.project.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.application.support.InMemoryProjectMilestoneRepository;
import tech.kayys.syirkah.project.application.support.InMemoryProjectRepository;
import tech.kayys.syirkah.project.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.project.domain.event.MilestoneCancelled;
import tech.kayys.syirkah.project.domain.event.MilestoneCreated;
import tech.kayys.syirkah.project.domain.event.MilestoneReached;
import tech.kayys.syirkah.project.domain.milestone.InvalidMilestoneStateException;
import tech.kayys.syirkah.project.domain.milestone.MilestoneStatus;
import tech.kayys.syirkah.project.domain.milestone.MilestoneType;
import tech.kayys.syirkah.project.domain.milestone.ProjectMilestone;
import tech.kayys.syirkah.project.domain.milestone.ProjectMilestoneId;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectType;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Project milestone command handlers")
class ProjectMilestoneCommandHandlerTest {

    private static final LocalDate PLANNED_DATE = LocalDate.of(2026, 6, 30);

    private static final DateRange PERIOD = new DateRange(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31)
    );

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryProjectMilestoneRepository milestones =
            new InMemoryProjectMilestoneRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    private final CreateProjectHandler createProject =
            new CreateProjectHandler(projects, events);
    private final CancelProjectHandler cancelProject =
            new CancelProjectHandler(projects, events);
    private final CreateMilestoneHandler createMilestone =
            new CreateMilestoneHandler(projects, milestones, events);
    private final ReachMilestoneHandler reachMilestone =
            new ReachMilestoneHandler(milestones, events);
    private final CancelMilestoneHandler cancelMilestone =
            new CancelMilestoneHandler(milestones, events);

    private ProjectId registerProject(String number) {
        return createProject.handle(
                new CreateProjectCommand(
                        number,
                        "Factory Construction",
                        ProjectType.CAPITAL_CIP,
                        null
                )
        ).await().indefinitely().orElseThrow();
    }

    private ProjectMilestoneId createMilestoneFor(ProjectId projectId, int sequence) {
        return createMilestone.handle(
                new CreateMilestoneCommand(
                        projectId,
                        sequence,
                        "Milestone " + sequence,
                        MilestoneType.DELIVERABLE,
                        PLANNED_DATE
                )
        ).await().indefinitely().orElseThrow();
    }

    private ProjectMilestone loadMilestone(ProjectMilestoneId id) {
        return milestones.findById(id).toCompletableFuture().join().orElseThrow();
    }

    private static String errorOf(Result<?> result) {
        var failure = assertInstanceOf(Result.Failure.class, result);
        return ((ApplicationError) failure.error()).code();
    }

    @Test
    @DisplayName("creates a milestone and publishes MilestoneCreated")
    void createsMilestone() {
        var projectId = registerProject("PRJ-301");

        var milestoneId = createMilestoneFor(projectId, 1);

        var saved = loadMilestone(milestoneId);

        assertEquals(projectId, saved.projectId());
        assertEquals(1, saved.sequence());
        assertEquals(MilestoneType.DELIVERABLE, saved.type());
        assertEquals(MilestoneStatus.PLANNED, saved.status());
        assertEquals(PLANNED_DATE, saved.plannedDate());
        assertNull(saved.actualDate());
        assertTrue(events.publishedTypes().contains(MilestoneCreated.class));
    }

    @Test
    @DisplayName("reaching a milestone records the actual date and publishes MilestoneReached")
    void reachesMilestone() {
        var projectId = registerProject("PRJ-302");
        var milestoneId = createMilestoneFor(projectId, 1);
        events.reset();

        var actual = LocalDate.of(2026, 7, 2);

        reachMilestone.handle(new ReachMilestoneCommand(milestoneId, actual))
                .await().indefinitely().orElseThrow();

        var saved = loadMilestone(milestoneId);

        assertEquals(MilestoneStatus.REACHED, saved.status());
        assertEquals(actual, saved.actualDate());
        assertEquals(List.of(MilestoneReached.class), events.publishedTypes());
    }

    @Test
    @DisplayName("cancelling a milestone publishes MilestoneCancelled")
    void cancelsMilestone() {
        var projectId = registerProject("PRJ-303");
        var milestoneId = createMilestoneFor(projectId, 1);
        events.reset();

        cancelMilestone.handle(new CancelMilestoneCommand(milestoneId))
                .await().indefinitely().orElseThrow();

        assertEquals(MilestoneStatus.CANCELLED, loadMilestone(milestoneId).status());
        assertEquals(List.of(MilestoneCancelled.class), events.publishedTypes());
    }

    @Test
    @DisplayName("a reached milestone cannot be cancelled")
    void reachedMilestoneCannotBeCancelled() {
        var projectId = registerProject("PRJ-304");
        var milestoneId = createMilestoneFor(projectId, 1);

        reachMilestone.handle(
                new ReachMilestoneCommand(milestoneId, LocalDate.of(2026, 6, 28))
        ).await().indefinitely().orElseThrow();

        assertThrows(
                InvalidMilestoneStateException.class,
                () -> cancelMilestone.handle(new CancelMilestoneCommand(milestoneId))
                        .await().indefinitely()
        );
    }

    @Test
    @DisplayName("an unknown project cannot receive milestones")
    void unknownProjectIsRejected() {
        var result = createMilestone.handle(
                new CreateMilestoneCommand(
                        ProjectId.generate(),
                        1,
                        "Milestone 1",
                        MilestoneType.APPROVAL,
                        PLANNED_DATE
                )
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("PROJECT_NOT_FOUND", errorOf(result));
    }

    @Test
    @DisplayName("a cancelled project cannot receive milestones")
    void cancelledProjectIsRejected() {
        var projectId = registerProject("PRJ-305");
        var project = projects.findById(projectId).toCompletableFuture().join().orElseThrow();

        project.plan(PERIOD);
        projects.save(project).toCompletableFuture().join();

        cancelProject.handle(new CancelProjectCommand(projectId))
                .await().indefinitely().orElseThrow();

        var result = createMilestone.handle(
                new CreateMilestoneCommand(
                        projectId,
                        1,
                        "Milestone 1",
                        MilestoneType.APPROVAL,
                        PLANNED_DATE
                )
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("PROJECT_NOT_ACCEPTING_MILESTONES", errorOf(result));
    }

    @Test
    @DisplayName("an unknown milestone yields MILESTONE_NOT_FOUND")
    void unknownMilestoneIsRejected() {
        var result = reachMilestone.handle(
                new ReachMilestoneCommand(ProjectMilestoneId.generate(), PLANNED_DATE)
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("MILESTONE_NOT_FOUND", errorOf(result));
    }
}
