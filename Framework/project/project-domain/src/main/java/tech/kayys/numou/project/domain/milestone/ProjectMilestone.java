package tech.kayys.syirkah.project.domain.milestone;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.project.domain.event.MilestoneCancelled;
import tech.kayys.syirkah.project.domain.event.MilestoneCreated;
import tech.kayys.syirkah.project.domain.event.MilestoneReached;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A significant point in a project - conceptually different from a
 * phase, which is a period of work.
 *
 * The milestone references its project by {@link ProjectId} only, so
 * it stays an independent consistency boundary.
 */
public final class ProjectMilestone
        extends AbstractAggregateRoot<ProjectMilestoneId> {

    private final ProjectId projectId;

    private final int sequence;

    private String name;

    private MilestoneType type;

    private MilestoneStatus status;

    private LocalDate plannedDate;

    private LocalDate actualDate;

    private ProjectMilestone(
            ProjectMilestoneId id,
            ProjectId projectId,
            int sequence,
            String name,
            MilestoneType type,
            LocalDate plannedDate
    ) {
        super(id);

        this.projectId =
                Objects.requireNonNull(
                        projectId,
                        "projectId cannot be null"
                );

        if (sequence < 1) {
            throw new IllegalArgumentException(
                    "Milestone sequence must be greater than zero"
            );
        }

        this.sequence = sequence;

        this.name = requireName(name);

        this.type =
                Objects.requireNonNull(
                        type,
                        "type cannot be null"
                );

        this.plannedDate =
                Objects.requireNonNull(
                        plannedDate,
                        "plannedDate cannot be null"
                );

        this.status = MilestoneStatus.PLANNED;
    }

    public static ProjectMilestone create(
            ProjectMilestoneId id,
            ProjectId projectId,
            int sequence,
            String name,
            MilestoneType type,
            LocalDate plannedDate
    ) {
        var milestone = new ProjectMilestone(
                id,
                projectId,
                sequence,
                name,
                type,
                plannedDate
        );

        milestone.raise(
                new MilestoneCreated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        projectId,
                        milestone.name
                )
        );

        return milestone;
    }

    public void reach(LocalDate actualDate) {

        requireStatus(MilestoneStatus.PLANNED);

        this.actualDate =
                Objects.requireNonNull(
                        actualDate,
                        "actualDate cannot be null"
                );

        this.status = MilestoneStatus.REACHED;

        raise(
                new MilestoneReached(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        actualDate
                )
        );
    }

    public void markMissed() {

        requireStatus(MilestoneStatus.PLANNED);

        this.status = MilestoneStatus.MISSED;
    }

    public void cancel() {

        if (status == MilestoneStatus.REACHED) {
            throw new InvalidMilestoneStateException(
                    "Reached milestone cannot be cancelled"
            );
        }

        if (status == MilestoneStatus.CANCELLED) {
            throw new InvalidMilestoneStateException(
                    "Milestone is already cancelled"
            );
        }

        this.status = MilestoneStatus.CANCELLED;

        raise(
                new MilestoneCancelled(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId
                )
        );
    }

    private void requireStatus(MilestoneStatus expected) {

        if (status != expected) {
            throw new InvalidMilestoneStateException(
                    "Milestone must be "
                            + expected
                            + " but was "
                            + status
            );
        }
    }

    private static String requireName(String name) {

        Objects.requireNonNull(
                name,
                "Milestone name cannot be null"
        );

        name = name.trim();

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "Milestone name cannot be blank"
            );
        }

        if (name.length() > 255) {
            throw new IllegalArgumentException(
                    "Milestone name cannot exceed 255 characters"
            );
        }

        return name;
    }

    public ProjectId projectId() {
        return projectId;
    }

    public int sequence() {
        return sequence;
    }

    public String name() {
        return name;
    }

    public MilestoneType type() {
        return type;
    }

    public MilestoneStatus status() {
        return status;
    }

    public LocalDate plannedDate() {
        return plannedDate;
    }

    public LocalDate actualDate() {
        return actualDate;
    }
}
