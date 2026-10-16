package tech.kayys.syirkah.project.domain.task;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Assigns a resource to a task.
 *
 * Assignment is its own aggregate: assigning, reassigning, and
 * unassigning happen on a lifecycle independent of the task itself.
 * The resource is referenced by type and id only — a task can be
 * assigned to an employee, a team, an external provider, a vehicle,
 * or an asset without {@code ProjectTask} ever changing.
 */
public final class TaskAssignment
        extends AbstractAggregateRoot<TaskAssignmentId> {

    private final ProjectId projectId;

    private final ProjectTaskId taskId;

    private final ResourceType resourceType;

    private final UUID resourceId;

    private String role;

    private Double plannedHours;

    private double actualHours;

    private AssignmentStatus status;

    private Instant assignedAt;

    private Instant unassignedAt;

    private TaskAssignment(
            TaskAssignmentId id,
            ProjectId projectId,
            ProjectTaskId taskId,
            ResourceType resourceType,
            UUID resourceId,
            String role,
            Double plannedHours
    ) {
        super(id);

        this.projectId = Objects.requireNonNull(
                projectId,
                "projectId cannot be null"
        );

        this.taskId = Objects.requireNonNull(
                taskId,
                "taskId cannot be null"
        );

        this.resourceType = Objects.requireNonNull(
                resourceType,
                "resourceType cannot be null"
        );

        this.resourceId = Objects.requireNonNull(
                resourceId,
                "resourceId cannot be null"
        );

        this.role = role;

        if (plannedHours != null && plannedHours < 0) {
            throw new IllegalArgumentException(
                    "Planned hours cannot be negative"
            );
        }

        this.plannedHours = plannedHours;
        this.actualHours = 0;
        this.status = AssignmentStatus.ASSIGNED;
        this.assignedAt = Instant.now();
    }

    public static TaskAssignment assign(
            TaskAssignmentId id,
            ProjectId projectId,
            ProjectTaskId taskId,
            ResourceType resourceType,
            UUID resourceId,
            String role,
            Double plannedHours
    ) {
        return new TaskAssignment(
                id,
                projectId,
                taskId,
                resourceType,
                resourceId,
                role,
                plannedHours
        );
    }

    public void recordActualHours(double hours) {

        if (hours < 0) {
            throw new IllegalArgumentException(
                    "Actual hours cannot be negative"
            );
        }

        this.actualHours = hours;
    }

    public void unassign() {

        if (status == AssignmentStatus.UNASSIGNED) {
            return;
        }

        this.status = AssignmentStatus.UNASSIGNED;
        this.unassignedAt = Instant.now();
    }

    public ProjectId projectId() {
        return projectId;
    }

    public ProjectTaskId taskId() {
        return taskId;
    }

    public ResourceType resourceType() {
        return resourceType;
    }

    public UUID resourceId() {
        return resourceId;
    }

    public String role() {
        return role;
    }

    public Double plannedHours() {
        return plannedHours;
    }

    public double actualHours() {
        return actualHours;
    }

    public AssignmentStatus status() {
        return status;
    }

    public Instant assignedAt() {
        return assignedAt;
    }

    public Instant unassignedAt() {
        return unassignedAt;
    }
}
