package tech.kayys.syirkah.project.domain.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.task.event.TaskBlocked;
import tech.kayys.syirkah.project.domain.task.event.TaskCancelled;
import tech.kayys.syirkah.project.domain.task.event.TaskCompleted;
import tech.kayys.syirkah.project.domain.task.event.TaskCreated;
import tech.kayys.syirkah.project.domain.task.event.TaskStarted;
import tech.kayys.syirkah.project.domain.task.event.TaskUnblocked;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ProjectTask aggregate")
class ProjectTaskTest {

    private static final DateRange PERIOD = new DateRange(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 3, 31)
    );

    private static ProjectTask task() {
        return ProjectTask.create(
                ProjectTaskId.newId(),
                ProjectId.generate(),
                null,
                null,
                null,
                TaskNumber.of("TSK-0001"),
                "Pour foundation",
                "Main raft pour",
                TaskType.WORK,
                TaskPriority.HIGH,
                PERIOD
        );
    }

    @Test
    void newTaskStartsAsTodoAndRaisesCreated() {
        var task = task();

        assertEquals(TaskStatus.TODO, task.status());
        assertEquals(0, task.progressPercentage());
        assertEquals("Pour foundation", task.title());
        assertInstanceOf(TaskCreated.class, task.getDomainEvents().getFirst());
        assertEquals("project.task-created",
                task.getDomainEvents().getFirst().eventType());
    }

    @Test
    void happyPathRaisesEachEvent() {
        var task = task();

        task.start();
        assertEquals(TaskStatus.IN_PROGRESS, task.status());

        task.block();
        assertEquals(TaskStatus.BLOCKED, task.status());

        task.unblock();
        assertEquals(TaskStatus.IN_PROGRESS, task.status());

        task.complete();
        assertEquals(TaskStatus.DONE, task.status());
        assertEquals(100, task.progressPercentage());

        var events = task.pullDomainEvents();

        assertTrue(events.stream().anyMatch(TaskStarted.class::isInstance));
        assertTrue(events.stream().anyMatch(TaskBlocked.class::isInstance));
        assertTrue(events.stream().anyMatch(TaskUnblocked.class::isInstance));
        assertTrue(events.stream().anyMatch(TaskCompleted.class::isInstance));
    }

    @Test
    void illegalTransitionsAreRejected() {
        var task = task();

        assertThrows(InvalidTaskStateException.class, task::block);
        assertThrows(InvalidTaskStateException.class, task::unblock);
        assertThrows(InvalidTaskStateException.class, task::complete);

        task.start();
        assertThrows(InvalidTaskStateException.class, task::start);
        assertThrows(InvalidTaskStateException.class, task::unblock);

        task.complete();
        assertThrows(InvalidTaskStateException.class, task::cancel);
    }

    @Test
    void updatingProgressToHundredCompletesTheTask() {
        var task = task();
        task.start();

        task.updateProgress(100);

        assertEquals(TaskStatus.DONE, task.status());
        assertEquals(100, task.progressPercentage());
    }

    @Test
    void cancelledTaskCannotBeUpdatedOrCompleted() {
        var task = task();

        task.cancel();
        assertEquals(TaskStatus.CANCELLED, task.status());
        assertTrue(task.pullDomainEvents().stream()
                .anyMatch(TaskCancelled.class::isInstance));

        assertThrows(InvalidTaskStateException.class, task::cancel);
        assertThrows(InvalidTaskStateException.class,
                () -> task.updateProgress(50));
    }

    @Test
    void rejectsInvalidConstruction() {
        assertThrows(IllegalArgumentException.class, () -> ProjectTask.create(
                ProjectTaskId.newId(),
                ProjectId.generate(),
                null, null, null,
                TaskNumber.of("  "),
                "Title", null,
                TaskType.WORK,
                TaskPriority.LOW,
                PERIOD
        ));

        assertThrows(IllegalArgumentException.class, () -> ProjectTask.create(
                ProjectTaskId.newId(),
                ProjectId.generate(),
                null, null, null,
                TaskNumber.of("TSK-0002"),
                "  ", null,
                TaskType.WORK,
                TaskPriority.LOW,
                PERIOD
        ));

        assertThrows(IllegalArgumentException.class,
                () -> TaskNumber.of("X".repeat(51)));
    }
}
