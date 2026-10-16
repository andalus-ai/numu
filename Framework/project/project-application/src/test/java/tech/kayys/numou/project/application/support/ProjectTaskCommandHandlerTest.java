package tech.kayys.syirkah.project.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.application.support.InMemoryProjectMilestoneRepository;
import tech.kayys.syirkah.project.application.support.InMemoryProjectPhaseRepository;
import tech.kayys.syirkah.project.application.support.InMemoryProjectRepository;
import tech.kayys.syirkah.project.application.support.InMemoryProjectTaskRepository;
import tech.kayys.syirkah.project.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.project.domain.project.Project;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.project.ProjectStatus;
import tech.kayys.syirkah.project.domain.project.ProjectType;
import tech.kayys.syirkah.project.domain.task.ProjectTask;
import tech.kayys.syirkah.project.domain.task.ProjectTaskId;
import tech.kayys.syirkah.project.domain.task.TaskNumber;
import tech.kayys.syirkah.project.domain.task.TaskPriority;
import tech.kayys.syirkah.project.domain.task.TaskStatus;
import tech.kayys.syirkah.project.domain.task.TaskType;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Project task command handlers")
class ProjectTaskCommandHandlerTest {

    private static final DateRange PERIOD = new DateRange(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 6, 30)
    );

    private final InMemoryProjectRepository projects = new InMemoryProjectRepository();
    private final InMemoryProjectPhaseRepository phases = new InMemoryProjectPhaseRepository();
    private final InMemoryProjectMilestoneRepository milestones =
            new InMemoryProjectMilestoneRepository();
    private final InMemoryProjectTaskRepository tasks = new InMemoryProjectTaskRepository();
    private final RecordingEventPublisher events = new RecordingEventPublisher();

    private final CreateProjectHandler createProject =
            new CreateProjectHandler(projects, events);
    private final StartProjectHandler startProject =
            new StartProjectHandler(projects, events);
    private final CreateTaskHandler createTask =
            new CreateTaskHandler(projects, phases, milestones, tasks, events);
    private final ChangeTaskStateHandler changeState =
            new ChangeTaskStateHandler(tasks, events);

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

    private ProjectTask loadTask(ProjectTaskId taskId) {
        return tasks.findById(taskId).toCompletableFuture().join().orElseThrow();
    }

    private CreateTaskCommand draftTask(ProjectId projectId, String number) {
        return new CreateTaskCommand(
                projectId,
                null,
                null,
                null,
                TaskNumber.of(number),
                "Pour foundation",
                "Main raft pour",
                TaskType.WORK,
                TaskPriority.HIGH,
                PERIOD
        );
    }

    @Test
    void createTaskOnOpenProject() {
        var projectId = registerStartedProject("PRJ-2026-000101");

        var result = createTask.handle(draftTask(projectId, "TSK-0001")).await().indefinitely();

        assertTrue(result.isSuccess());

        var task = loadTask(result.orElseThrow());
        assertEquals(TaskStatus.TODO, task.status());
        assertEquals(0, task.progressPercentage());
        assertEquals("Pour foundation", task.title());
    }

    @Test
    void createTaskOnUnknownProject() {
        var result = createTask.handle(
                draftTask(ProjectId.generate(), "TSK-0002")
        ).await().indefinitely();

        assertTrue(result.isFailure());
    }

    @Test
    void taskFullLifecycle() {
        var projectId = registerStartedProject("PRJ-2026-000102");

        var taskId = createTask.handle(draftTask(projectId, "TSK-0003"))
                .await().indefinitely().orElseThrow();

        assertTrue(changeState.start(new StartTaskCommand(taskId)).await().indefinitely()
                .isSuccess());
        assertEquals(TaskStatus.IN_PROGRESS, loadTask(taskId).status());

        assertTrue(changeState.block(new BlockTaskCommand(taskId)).await().indefinitely()
                .isSuccess());
        assertEquals(TaskStatus.BLOCKED, loadTask(taskId).status());

        assertTrue(changeState.unblock(new UnblockTaskCommand(taskId)).await().indefinitely()
                .isSuccess());
        assertEquals(TaskStatus.IN_PROGRESS, loadTask(taskId).status());

        assertTrue(changeState.complete(new CompleteTaskCommand(taskId)).await().indefinitely()
                .isSuccess());

        var done = loadTask(taskId);
        assertEquals(TaskStatus.DONE, done.status());
        assertEquals(100, done.progressPercentage());

        // Starting a completed task must fail through the handler.
        try {
            changeState.start(new StartTaskCommand(taskId)).await().indefinitely();
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("Only TODO tasks can be started")
                    || expected.getCause() instanceof
                    tech.kayys.syirkah.project.domain.task.InvalidTaskStateException);
            return;
        }

        throw new AssertionError("Starting a completed task should have failed");
    }

    @Test
    void cancelPreventsFurtherTransitions() {
        var projectId = registerStartedProject("PRJ-2026-000103");

        var taskId = createTask.handle(draftTask(projectId, "TSK-0004"))
                .await().indefinitely().orElseThrow();

        assertTrue(changeState.cancel(new CancelTaskCommand(taskId)).await().indefinitely()
                .isSuccess());
        assertEquals(TaskStatus.CANCELLED, loadTask(taskId).status());

        // Any further transition on a cancelled task must fail.
        try {
            changeState.complete(new CompleteTaskCommand(taskId)).await().indefinitely();
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage().contains("Only in-progress tasks can be completed")
                    || expected.getCause() instanceof
                    tech.kayys.syirkah.project.domain.task.InvalidTaskStateException);
            return;
        }

        throw new AssertionError("Completing a cancelled task should have failed");
    }

    @Test
    void operationsOnUnknownTaskFail() {
        var result = changeState.start(new StartTaskCommand(ProjectTaskId.newId()))
                .await().indefinitely();

        assertTrue(result.isFailure());
        assertFalse(result.isSuccess());
    }
}