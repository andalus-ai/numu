package tech.kayys.syirkah.project.domain.task;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TaskAssignment + TaskDependency")
class TaskAssignmentTest {

    private static final ProjectId PROJECT = ProjectId.generate();

    @Test
    void assignmentTracksPlannedAndActualHours() {
        var assignment = TaskAssignment.assign(
                TaskAssignmentId.newId(),
                PROJECT,
                ProjectTaskId.newId(),
                ResourceType.EMPLOYEE,
                UUID.randomUUID(),
                "Foreman",
                16.0
        );

        assertEquals(AssignmentStatus.ASSIGNED, assignment.status());
        assertEquals(0, assignment.actualHours());
        assertTrue(assignment.unassignedAt() == null);

        assignment.recordActualHours(10.5);
        assertEquals(10.5, assignment.actualHours());

        assignment.unassign();
        assertEquals(AssignmentStatus.UNASSIGNED, assignment.status());
        assertTrue(assignment.unassignedAt() != null);

        // Unassigning twice is a no-op, not an error.
        assignment.unassign();
        assertEquals(AssignmentStatus.UNASSIGNED, assignment.status());

        assertThrows(IllegalArgumentException.class,
                () -> assignment.recordActualHours(-1));
        assertThrows(IllegalArgumentException.class, () -> TaskAssignment.assign(
                TaskAssignmentId.newId(),
                PROJECT,
                ProjectTaskId.newId(),
                ResourceType.EMPLOYEE,
                UUID.randomUUID(),
                "Foreman",
                -5.0
        ));
    }

    @Test
    void assignmentSupportsNonEmployeeResources() {
        var assignment = TaskAssignment.assign(
                TaskAssignmentId.newId(),
                PROJECT,
                ProjectTaskId.newId(),
                ResourceType.VEHICLE,
                UUID.randomUUID(),
                null,
                null
        );

        assertEquals(ResourceType.VEHICLE, assignment.resourceType());
        assertTrue(assignment.plannedHours() == null);
    }

    @Test
    void dependencyRejectsSelfReferenceAndNegativeLag() {
        var taskId = ProjectTaskId.newId();

        assertThrows(IllegalArgumentException.class, () -> TaskDependency.link(
                TaskDependencyId.newId(),
                PROJECT,
                taskId,
                taskId,
                TaskDependencyType.FINISH_TO_START,
                0
        ));

        assertThrows(IllegalArgumentException.class, () -> TaskDependency.link(
                TaskDependencyId.newId(),
                PROJECT,
                taskId,
                ProjectTaskId.newId(),
                TaskDependencyType.START_TO_START,
                -1
        ));
    }
}
