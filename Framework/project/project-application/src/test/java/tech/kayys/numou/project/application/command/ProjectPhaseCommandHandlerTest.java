package tech.kayys.syirkah.project.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.application.support.InMemoryProjectPhaseRepository;
import tech.kayys.syirkah.project.application.support.InMemoryProjectRepository;
import tech.kayys.syirkah.project.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.project.domain.event.PhaseCompleted;
import tech.kayys.syirkah.project.domain.event.PhaseCreated;
import tech.kayys.syirkah.project.domain.event.PhaseStarted;
import tech.kayys.syirkah.project.domain.phase.InvalidPhaseStateException;
import tech.kayys.syirkah.project.domain.phase.PhaseStatus;
import tech.kayys.syirkah.project.domain.phase.PhaseType;
import tech.kayys.syirkah.project.domain.phase.ProjectPhase;
import tech.kayys.syirkah.project.domain.phase.ProjectPhaseId;
import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectStatus;
import tech.kayys.syirkah.project.domain.project.ProjectType;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Project phase command handlers")
class ProjectPhaseCommandHandlerTest {

    private static final DateRange PERIOD = new DateRange(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 6, 30)
    );

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryProjectPhaseRepository phases =
            new InMemoryProjectPhaseRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    private final CreateProjectHandler createProject =
            new CreateProjectHandler(projects, events);
    private final StartProjectHandler startProject =
            new StartProjectHandler(projects, events);
    private final CompleteProjectHandler completeProject =
            new CompleteProjectHandler(projects, events);
    private final CreatePhaseHandler createPhase =
            new CreatePhaseHandler(projects, phases, events);
    private final StartPhaseHandler startPhase =
            new StartPhaseHandler(phases, events);
    private final CompletePhaseHandler completePhase =
            new CompletePhaseHandler(phases, events);

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

    private ProjectId registerCompletedProject(String number) {
        var projectId = registerProject(number);
        var project = loadProject(projectId);

        project.plan(PERIOD);
        projects.save(project).toCompletableFuture().join();

        startProject.handle(new StartProjectCommand(projectId)).await().indefinitely();
        completeProject.handle(new CompleteProjectCommand(projectId)).await().indefinitely();

        return projectId;
    }

    private ProjectId registerStartedProject(String number) {
        var projectId = registerProject(number);
        var project = loadProject(projectId);

        project.plan(PERIOD);
        projects.save(project).toCompletableFuture().join();

        startProject.handle(new StartProjectCommand(projectId)).await().indefinitely();

        return projectId;
    }

    private Project loadProject(ProjectId projectId) {
        return projects.findById(projectId).toCompletableFuture().join().orElseThrow();
    }

    private ProjectPhase loadPhase(ProjectPhaseId phaseId) {
        return phases.findById(phaseId).toCompletableFuture().join().orElseThrow();
    }

    private ProjectPhaseId createPhaseFor(ProjectId projectId, int sequence) {
        return createPhase.handle(
                new CreatePhaseCommand(
                        projectId,
                        sequence,
                        "Phase " + sequence,
                        PhaseType.EXECUTION
                )
        ).await().indefinitely().orElseThrow();
    }

    private void planPhase(ProjectPhaseId phaseId) {
        var phase = loadPhase(phaseId);
        phase.plan(PERIOD);
        phases.save(phase).toCompletableFuture().join();
    }

    private static String errorOf(Result<?> result) {
        var failure = assertInstanceOf(Result.Failure.class, result);
        return ((ApplicationError) failure.error()).code();
    }

    @Test
    @DisplayName("creates a phase for an open project and publishes PhaseCreated")
    void createsPhase() {
        var projectId = registerProject("PRJ-201");

        var phaseId = createPhaseFor(projectId, 1);

        var saved = loadPhase(phaseId);

        assertEquals(projectId, saved.projectId());
        assertEquals(1, saved.sequence());
        assertEquals(PhaseType.EXECUTION, saved.type());
        assertEquals(PhaseStatus.DRAFT, saved.status());
        assertTrue(events.publishedTypes().contains(PhaseCreated.class));
    }

    @Test
    @DisplayName("phases are listed by project in sequence order")
    void phasesAreListedByProject() {
        var projectId = registerProject("PRJ-202");
        createPhaseFor(projectId, 2);
        createPhaseFor(projectId, 1);

        var ordered = phases.findByProjectId(projectId)
                .toCompletableFuture().join();

        assertEquals(
                List.of(1, 2),
                ordered.stream().map(ProjectPhase::sequence).toList()
        );
    }

    @Test
    @DisplayName("an unknown project cannot receive phases")
    void unknownProjectIsRejected() {
        var result = createPhase.handle(
                new CreatePhaseCommand(
                        ProjectId.generate(),
                        1,
                        "Phase 1",
                        PhaseType.PLANNING
                )
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("PROJECT_NOT_FOUND", errorOf(result));
    }

    @Test
    @DisplayName("a completed project cannot receive phases")
    void completedProjectIsRejected() {
        var projectId = registerCompletedProject("PRJ-203");

        assertEquals(ProjectStatus.COMPLETED, loadProject(projectId).status());

        var result = createPhase.handle(
                new CreatePhaseCommand(projectId, 1, "Phase 1", PhaseType.PLANNING)
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("PROJECT_NOT_ACCEPTING_PHASES", errorOf(result));
    }

    @Test
    @DisplayName("a planned phase can be started and completed")
    void startAndCompletePhase() {
        var projectId = registerStartedProject("PRJ-204");
        var phaseId = createPhaseFor(projectId, 1);
        planPhase(phaseId);
        events.reset();

        startPhase.handle(new StartPhaseCommand(phaseId))
                .await().indefinitely().orElseThrow();
        completePhase.handle(new CompletePhaseCommand(phaseId))
                .await().indefinitely().orElseThrow();

        assertEquals(PhaseStatus.COMPLETED, loadPhase(phaseId).status());
        assertEquals(
                List.of(PhaseStarted.class, PhaseCompleted.class),
                events.publishedTypes()
        );
    }

    @Test
    @DisplayName("an unknown phase yields PHASE_NOT_FOUND")
    void unknownPhaseIsRejected() {
        var result = startPhase.handle(
                new StartPhaseCommand(ProjectPhaseId.generate())
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("PHASE_NOT_FOUND", errorOf(result));
    }

    @Test
    @DisplayName("an illegal phase transition surfaces as a domain exception")
    void illegalTransitionIsRejected() {
        var projectId = registerProject("PRJ-205");
        var phaseId = createPhaseFor(projectId, 1);

        assertThrows(
                InvalidPhaseStateException.class,
                () -> startPhase.handle(new StartPhaseCommand(phaseId))
                        .await().indefinitely(),
                "a DRAFT phase must be planned before it can start"
        );
    }
}
