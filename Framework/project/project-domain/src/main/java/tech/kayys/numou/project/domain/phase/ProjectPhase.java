package tech.kayys.syirkah.project.domain.phase;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.project.domain.event.PhaseCompleted;
import tech.kayys.syirkah.project.domain.event.PhaseCreated;
import tech.kayys.syirkah.project.domain.event.PhaseStarted;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A period of work inside a project.
 *
 * The phase knows which project it belongs to through
 * {@link ProjectId}, but it never holds a {@code Project} reference:
 * that would create an aggregate graph
 * {@code Project -> Phase -> Project -> ...} and load the whole
 * project just to change one phase.
 */
public final class ProjectPhase
        extends AbstractAggregateRoot<ProjectPhaseId> {

    private final ProjectId projectId;

    private final int sequence;

    private String name;

    private PhaseType type;

    private PhaseStatus status;

    private DateRange plannedPeriod;

    private ProjectPhase(
            ProjectPhaseId id,
            ProjectId projectId,
            int sequence,
            String name,
            PhaseType type
    ) {
        super(id);

        this.projectId =
                Objects.requireNonNull(
                        projectId,
                        "projectId cannot be null"
                );

        if (sequence < 1) {
            throw new IllegalArgumentException(
                    "Phase sequence must be greater than zero"
            );
        }

        this.sequence = sequence;

        this.name = requireName(name);

        this.type =
                Objects.requireNonNull(
                        type,
                        "type cannot be null"
                );

        this.status = PhaseStatus.DRAFT;
    }

    public static ProjectPhase create(
            ProjectPhaseId id,
            ProjectId projectId,
            int sequence,
            String name,
            PhaseType type
    ) {
        var phase = new ProjectPhase(
                id,
                projectId,
                sequence,
                name,
                type
        );

        phase.raise(
                new PhaseCreated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        projectId,
                        phase.name
                )
        );

        return phase;
    }

    public void plan(DateRange plannedPeriod) {

        requireStatus(PhaseStatus.DRAFT);

        this.plannedPeriod =
                Objects.requireNonNull(
                        plannedPeriod,
                        "plannedPeriod cannot be null"
                );

        this.status = PhaseStatus.PLANNED;
    }

    public void start() {

        requireStatus(PhaseStatus.PLANNED);

        this.status = PhaseStatus.ACTIVE;

        raise(
                new PhaseStarted(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId
                )
        );
    }

    public void putOnHold() {

        requireStatus(PhaseStatus.ACTIVE);

        this.status = PhaseStatus.ON_HOLD;
    }

    public void resume() {

        requireStatus(PhaseStatus.ON_HOLD);

        this.status = PhaseStatus.ACTIVE;
    }

    public void complete() {

        requireStatus(PhaseStatus.ACTIVE);

        this.status = PhaseStatus.COMPLETED;

        raise(
                new PhaseCompleted(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId
                )
        );
    }

    public void cancel() {

        if (status == PhaseStatus.COMPLETED) {
            throw new InvalidPhaseStateException(
                    "Completed phase cannot be cancelled"
            );
        }

        if (status == PhaseStatus.CANCELLED) {
            throw new InvalidPhaseStateException(
                    "Phase is already cancelled"
            );
        }

        this.status = PhaseStatus.CANCELLED;
    }

    private void requireStatus(PhaseStatus expected) {

        if (status != expected) {
            throw new InvalidPhaseStateException(
                    "Phase must be "
                            + expected
                            + " but was "
                            + status
            );
        }
    }

    private static String requireName(String name) {

        Objects.requireNonNull(
                name,
                "Phase name cannot be null"
        );

        name = name.trim();

        if (name.isBlank()) {
            throw new IllegalArgumentException(
                    "Phase name cannot be blank"
            );
        }

        if (name.length() > 255) {
            throw new IllegalArgumentException(
                    "Phase name cannot exceed 255 characters"
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

    public PhaseType type() {
        return type;
    }

    public PhaseStatus status() {
        return status;
    }

    public DateRange plannedPeriod() {
        return plannedPeriod;
    }
}
