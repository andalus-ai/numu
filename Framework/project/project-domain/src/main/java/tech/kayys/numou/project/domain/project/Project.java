package tech.kayys.syirkah.project.domain.project;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.domain.event.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Project
        extends AbstractAggregateRoot<ProjectId> {

    private ProjectNumber projectNumber;

    private String name;

    private ProjectType type;

    private ProjectStatus status;

    /**
     * Reference to another bounded context.
     *
     * This is deliberately only an ID.
     * Project does not contain Customer.
     */
    private UUID customerId;

    private DateRange plannedPeriod;

    private Project(
            ProjectId id,
            ProjectNumber projectNumber,
            String name,
            ProjectType type,
            UUID customerId
    ) {
        super(id);

        this.projectNumber =
                Objects.requireNonNull(
                        projectNumber,
                        "projectNumber cannot be null"
                );

        this.name = requireName(name);

        this.type =
                Objects.requireNonNull(
                        type,
                        "type cannot be null"
                );

        this.customerId = customerId;

        this.status = ProjectStatus.DRAFT;
    }

    public static Project create(
            ProjectId id,
            ProjectNumber projectNumber,
            String name,
            ProjectType type,
            UUID customerId
    ) {
        var project = new Project(
                id,
                projectNumber,
                name,
                type,
                customerId
        );

        project.raise(
                new ProjectCreated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        projectNumber.value()
                )
        );

        return project;
    }

    public void plan(DateRange plannedPeriod) {

        requireStatus(ProjectStatus.DRAFT);

        this.plannedPeriod =
                Objects.requireNonNull(
                        plannedPeriod,
                        "plannedPeriod cannot be null"
                );

        this.status = ProjectStatus.PLANNED;

        raise(
                new ProjectPlanned(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        plannedPeriod
                )
        );
    }

    public void start() {

        requireStatus(ProjectStatus.PLANNED);

        this.status = ProjectStatus.ACTIVE;

        raise(
                new ProjectStarted(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void putOnHold() {

        requireStatus(ProjectStatus.ACTIVE);

        this.status = ProjectStatus.ON_HOLD;

        raise(
                new ProjectPutOnHold(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void resume() {

        requireStatus(ProjectStatus.ON_HOLD);

        this.status = ProjectStatus.ACTIVE;

        raise(
                new ProjectResumed(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void complete() {

        requireStatus(ProjectStatus.ACTIVE);

        this.status = ProjectStatus.COMPLETED;

        raise(
                new ProjectCompleted(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    public void cancel() {

        if (status == ProjectStatus.COMPLETED) {
            throw new InvalidProjectStateException(
                    "Completed project cannot be cancelled"
            );
        }

        if (status == ProjectStatus.CANCELLED) {
            throw new InvalidProjectStateException(
                    "Project is already cancelled"
            );
        }

        this.status = ProjectStatus.CANCELLED;

        raise(
                new ProjectCancelled(
                        UUID.randomUUID(),
                        Instant.now(),
                        id()
                )
        );
    }

    private void requireStatus(ProjectStatus expected) {

        if (status != expected) {
            throw new InvalidProjectStateException(
                    "Project must be "
                            + expected
                            + " but was "
                            + status
            );
        }
    }

    private static String requireName(String name) {

        Objects.requireNonNull(
                name,
                "Project name cannot be null"
        );

        name = name.trim();

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "Project name cannot be blank"
            );
        }

        if (name.length() > 255) {
            throw new IllegalArgumentException(
                    "Project name cannot exceed 255 characters"
            );
        }

        return name;
    }

    public ProjectNumber projectNumber() {
        return projectNumber;
    }

    public String name() {
        return name;
    }

    public ProjectType type() {
        return type;
    }

    public ProjectStatus status() {
        return status;
    }

    public UUID customerId() {
        return customerId;
    }

    public DateRange plannedPeriod() {
        return plannedPeriod;
    }
}
