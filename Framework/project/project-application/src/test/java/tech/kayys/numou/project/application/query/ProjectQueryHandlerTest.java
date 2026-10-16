package tech.kayys.syirkah.project.application.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.ApplicationErrorException;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.application.command.CreateMilestoneCommand;
import tech.kayys.syirkah.project.application.command.CreateMilestoneHandler;
import tech.kayys.syirkah.project.application.command.CreatePhaseCommand;
import tech.kayys.syirkah.project.application.command.CreatePhaseHandler;
import tech.kayys.syirkah.project.application.command.CreateProjectCommand;
import tech.kayys.syirkah.project.application.command.CreateProjectHandler;
import tech.kayys.syirkah.project.application.support.InMemoryProjectMilestoneRepository;
import tech.kayys.syirkah.project.application.support.InMemoryProjectPhaseRepository;
import tech.kayys.syirkah.project.application.support.InMemoryProjectRepository;
import tech.kayys.syirkah.project.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.project.domain.milestone.MilestoneType;
import tech.kayys.syirkah.project.domain.phase.PhaseType;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectStatus;
import tech.kayys.syirkah.project.domain.project.ProjectType;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Project query handlers")
class ProjectQueryHandlerTest {

    private static final UUID CUSTOMER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryProjectPhaseRepository phases =
            new InMemoryProjectPhaseRepository();
    private final InMemoryProjectMilestoneRepository milestones =
            new InMemoryProjectMilestoneRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    private final CreateProjectHandler createProject =
            new CreateProjectHandler(projects, events);
    private final CreatePhaseHandler createPhase =
            new CreatePhaseHandler(projects, phases, events);
    private final CreateMilestoneHandler createMilestone =
            new CreateMilestoneHandler(projects, milestones, events);

    private final GetProjectQueryHandler getProject =
            new GetProjectQueryHandler(projects);
    private final ListProjectPhasesQueryHandler listPhases =
            new ListProjectPhasesQueryHandler(phases);
    private final ListProjectMilestonesQueryHandler listMilestones =
            new ListProjectMilestonesQueryHandler(milestones);

    private ProjectId registerProject() {
        return createProject.handle(
                new CreateProjectCommand(
                        "PRJ-401",
                        "E-Commerce Replatform",
                        ProjectType.CLIENT_BILLABLE,
                        CUSTOMER_ID
                )
        ).await().indefinitely().orElseThrow();
    }

    @Test
    @DisplayName("getProject maps the aggregate to a read model")
    void getsProject() {
        var projectId = registerProject();

        var view = getProject.handle(new GetProjectQuery(projectId))
                .await().indefinitely();

        assertEquals(projectId.value(), view.projectId());
        assertEquals("PRJ-401", view.projectNumber());
        assertEquals("E-Commerce Replatform", view.name());
        assertEquals(ProjectStatus.DRAFT, view.status());
        assertEquals(CUSTOMER_ID, view.customerId());
    }

    @Test
    @DisplayName("getProject fails with PROJECT_NOT_FOUND for an unknown id")
    void getUnknownProjectFails() {
        var exception = assertThrows(
                ApplicationErrorException.class,
                () -> getProject.handle(new GetProjectQuery(ProjectId.generate()))
                        .await().indefinitely()
        );

        assertEquals("PROJECT_NOT_FOUND", exception.error().code());
    }

    @Test
    @DisplayName("phases and milestones are listed in sequence order")
    void listsPhasesAndMilestones() {
        var projectId = registerProject();

        createPhase.handle(
                new CreatePhaseCommand(projectId, 2, "Installation", PhaseType.EXECUTION)
        ).await().indefinitely().orElseThrow();
        createPhase.handle(
                new CreatePhaseCommand(projectId, 1, "Foundation", PhaseType.PREPARATION)
        ).await().indefinitely().orElseThrow();

        createMilestone.handle(
                new CreateMilestoneCommand(
                        projectId,
                        2,
                        "Electrical Energized",
                        MilestoneType.COMMISSIONING,
                        LocalDate.of(2026, 8, 31)
                )
        ).await().indefinitely().orElseThrow();
        createMilestone.handle(
                new CreateMilestoneCommand(
                        projectId,
                        1,
                        "Foundation Complete",
                        MilestoneType.DELIVERABLE,
                        LocalDate.of(2026, 5, 31)
                )
        ).await().indefinitely().orElseThrow();

        var phaseViews = listPhases.handle(new ListProjectPhasesQuery(projectId))
                .await().indefinitely();
        var milestoneViews = listMilestones.handle(new ListProjectMilestonesQuery(projectId))
                .await().indefinitely();

        assertEquals(2, phaseViews.size());
        assertEquals("Foundation", phaseViews.getFirst().name());
        assertEquals("Installation", phaseViews.getLast().name());

        assertEquals(2, milestoneViews.size());
        assertEquals("Foundation Complete", milestoneViews.getFirst().name());
        assertEquals(LocalDate.of(2026, 5, 31), milestoneViews.getFirst().plannedDate());
    }

    @Test
    @DisplayName("an empty project yields empty phase and milestone lists")
    void listsAreEmptyForNewProject() {
        var projectId = registerProject();

        assertTrue(listPhases.handle(new ListProjectPhasesQuery(projectId))
                .await().indefinitely().isEmpty());
        assertTrue(listMilestones.handle(new ListProjectMilestonesQuery(projectId))
                .await().indefinitely().isEmpty());
    }
}
