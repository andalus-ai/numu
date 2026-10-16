package tech.kayys.syirkah.project.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.ApplicationErrorException;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.application.support.InMemoryProjectRepository;
import tech.kayys.syirkah.project.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.project.domain.event.ProjectCancelled;
import tech.kayys.syirkah.project.domain.event.ProjectCompleted;
import tech.kayys.syirkah.project.domain.event.ProjectCreated;
import tech.kayys.syirkah.project.domain.event.ProjectPlanned;
import tech.kayys.syirkah.project.domain.event.ProjectPutOnHold;
import tech.kayys.syirkah.project.domain.event.ProjectResumed;
import tech.kayys.syirkah.project.domain.event.ProjectStarted;
import tech.kayys.syirkah.project.domain.project.InvalidProjectStateException;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectStatus;
import tech.kayys.syirkah.project.domain.project.ProjectType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Project lifecycle command handlers")
class ProjectLifecycleCommandHandlerTest {

    private static final UUID CUSTOMER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final DateRange PLANNED_PERIOD =
            new DateRange(
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 12, 31)
            );

    private final InMemoryProjectRepository repository =
            new InMemoryProjectRepository();

    private final RecordingEventPublisher eventPublisher =
            new RecordingEventPublisher();

    private final CreateProjectHandler createHandler =
            new CreateProjectHandler(repository, eventPublisher);
    private final PlanProjectHandler planHandler =
            new PlanProjectHandler(repository, eventPublisher);
    private final StartProjectHandler startHandler =
            new StartProjectHandler(repository, eventPublisher);
    private final PutProjectOnHoldHandler putOnHoldHandler =
            new PutProjectOnHoldHandler(repository, eventPublisher);
    private final ResumeProjectHandler resumeHandler =
            new ResumeProjectHandler(repository, eventPublisher);
    private final CompleteProjectHandler completeHandler =
            new CompleteProjectHandler(repository, eventPublisher);
    private final CancelProjectHandler cancelHandler =
            new CancelProjectHandler(repository, eventPublisher);

    private ProjectId register(String projectNumber) {
        return createHandler.handle(
                new CreateProjectCommand(
                        projectNumber,
                        "Core Banking Cloud",
                        ProjectType.CLIENT_BILLABLE,
                        CUSTOMER_ID
                )
        ).await().indefinitely().orElseThrow();
    }

    @Test
    @DisplayName("create persists the aggregate and publishes ProjectCreated")
    void createPersistsAndPublishes() {
        var projectId = register("PRJ-101");

        var saved = repository.findById(projectId)
                .toCompletableFuture().join().orElseThrow();

        assertEquals(ProjectStatus.DRAFT, saved.status());
        assertEquals("Core Banking Cloud", saved.name());
        assertTrue(
                eventPublisher.publishedTypes().contains(ProjectCreated.class),
                "ProjectCreated must be published"
        );
    }

    @Test
    @DisplayName("a project number can only be used once")
    void duplicateProjectNumberIsRejected() {
        register("PRJ-101");

        var result = createHandler.handle(
                new CreateProjectCommand(
                        "PRJ-101",
                        "Second attempt",
                        ProjectType.INTERNAL,
                        null
                )
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals(
                "PROJECT_NUMBER_ALREADY_USED",
                errorOf(result).code()
        );
    }

    @Test
    @DisplayName("the whole lifecycle publishes one event per transition, in order")
    void fullLifecyclePublishesEventsInOrder() {
        var projectId = register("PRJ-101");

        planHandler.handle(new PlanProjectCommand(projectId, PLANNED_PERIOD))
                .await().indefinitely().orElseThrow();
        startHandler.handle(new StartProjectCommand(projectId))
                .await().indefinitely().orElseThrow();
        putOnHoldHandler.handle(new PutProjectOnHoldCommand(projectId))
                .await().indefinitely().orElseThrow();
        resumeHandler.handle(new ResumeProjectCommand(projectId))
                .await().indefinitely().orElseThrow();
        completeHandler.handle(new CompleteProjectCommand(projectId))
                .await().indefinitely().orElseThrow();

        assertEquals(
                List.of(
                        ProjectCreated.class,
                        ProjectPlanned.class,
                        ProjectStarted.class,
                        ProjectPutOnHold.class,
                        ProjectResumed.class,
                        ProjectCompleted.class
                ),
                eventPublisher.publishedTypes()
        );

        var saved = repository.findById(projectId)
                .toCompletableFuture().join().orElseThrow();

        assertEquals(ProjectStatus.COMPLETED, saved.status());
        assertEquals(PLANNED_PERIOD, saved.plannedPeriod());
    }

    @Test
    @DisplayName("cancel publishes ProjectCancelled")
    void cancelPublishesEvent() {
        var projectId = register("PRJ-101");

        planHandler.handle(new PlanProjectCommand(projectId, PLANNED_PERIOD))
                .await().indefinitely().orElseThrow();
        cancelHandler.handle(new CancelProjectCommand(projectId))
                .await().indefinitely().orElseThrow();

        assertTrue(eventPublisher.publishedTypes().contains(ProjectCancelled.class));
        assertEquals(
                ProjectStatus.CANCELLED,
                repository.findById(projectId)
                        .toCompletableFuture().join()
                        .orElseThrow()
                        .status()
        );
    }

    @Test
    @DisplayName("an unknown project yields a typed PROJECT_NOT_FOUND failure")
    void unknownProjectReturnsNotFound() {
        var result = startHandler.handle(
                new StartProjectCommand(ProjectId.generate())
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("PROJECT_NOT_FOUND", errorOf(result).code());
        assertThrows(ApplicationErrorException.class, result::orElseThrow);
    }

    @Test
    @DisplayName("an illegal transition surfaces as a domain exception")
    void illegalTransitionIsRejected() {
        var projectId = register("PRJ-101");

        assertThrows(
                InvalidProjectStateException.class,
                () -> completeHandler.handle(new CompleteProjectCommand(projectId))
                        .await().indefinitely(),
                "a DRAFT project cannot be completed"
        );
    }

    private static ApplicationError errorOf(Result<ProjectId> result) {
        var failure = assertInstanceOf(Result.Failure.class, result);
        return (ApplicationError) failure.error();
    }
}
