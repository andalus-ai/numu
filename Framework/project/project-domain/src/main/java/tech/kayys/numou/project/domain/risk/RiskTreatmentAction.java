package tech.kayys.syirkah.project.domain.risk;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.domain.risk.event.RiskActionCompleted;
import tech.kayys.syirkah.project.domain.risk.event.RiskActionCreated;
import tech.kayys.syirkah.project.domain.risk.event.RiskActionStarted;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One action taken against a risk (response plan, mitigation step,
 * contingency drill …).
 *
 * Actions are their own aggregate: a single risk may accumulate many
 * actions over months, and they are created, started, completed and
 * cancelled independently of the risk's own lifecycle. Persisted
 * separately from {@link Risk} so the risk aggregate stays small.
 */
public final class RiskTreatmentAction
        extends AbstractAggregateRoot<RiskTreatmentActionId> {

    private final ProjectId projectId;
    private final RiskId riskId;

    private final String title;
    private final String description;

    private final UUID ownerId;

    private final LocalDate dueDate;

    private final Money estimatedCost;

    private RiskActionStatus status;

    private RiskTreatmentAction(
            RiskTreatmentActionId id,
            ProjectId projectId,
            RiskId riskId,
            String title,
            String description,
            UUID ownerId,
            LocalDate dueDate,
            Money estimatedCost
    ) {
        super(id);

        this.projectId = Objects.requireNonNull(
                projectId,
                "projectId cannot be null"
        );

        this.riskId = Objects.requireNonNull(
                riskId,
                "riskId cannot be null"
        );

        this.title = requireText(title, "title");
        this.description = requireText(description, "description");

        this.ownerId = Objects.requireNonNull(ownerId, "ownerId cannot be null");

        this.dueDate = Objects.requireNonNull(
                dueDate,
                "dueDate cannot be null"
        );

        this.estimatedCost = Objects.requireNonNull(
                estimatedCost,
                "estimatedCost cannot be null"
        );

        if (estimatedCost.amount().signum() < 0) {
            throw new IllegalArgumentException(
                    "Estimated cost cannot be negative"
            );
        }

        this.status = RiskActionStatus.OPEN;

        raise(new RiskActionCreated(
                UUID.randomUUID(),
                Instant.now(),
                id,
                riskId,
                projectId,
                this.title,
                this.dueDate
        ));
    }

    public static RiskTreatmentAction create(
            RiskTreatmentActionId id,
            ProjectId projectId,
            RiskId riskId,
            String title,
            String description,
            UUID ownerId,
            LocalDate dueDate,
            Money estimatedCost
    ) {
        return new RiskTreatmentAction(
                id,
                projectId,
                riskId,
                title,
                description,
                ownerId,
                dueDate,
                estimatedCost
        );
    }

    public void start() {
        if (status != RiskActionStatus.OPEN) {
            throw new InvalidRiskStateException("Only an open action can start");
        }

        this.status = RiskActionStatus.IN_PROGRESS;

        raise(new RiskActionStarted(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                riskId,
                projectId
        ));
    }

    public void complete() {
        if (status != RiskActionStatus.IN_PROGRESS) {
            throw new InvalidRiskStateException(
                    "Only an in-progress action can complete"
            );
        }

        this.status = RiskActionStatus.COMPLETED;

        raise(new RiskActionCompleted(
                UUID.randomUUID(),
                Instant.now(),
                id(),
                riskId,
                projectId
        ));
    }

    /**
     * Deliberately raises no event: the P10.10 event catalog only
     * defines created/started/completed for actions, so cancellation
     * stays a quiet bookkeeping transition.
     */
    public void cancel() {
        if (status == RiskActionStatus.COMPLETED) {
            throw new InvalidRiskStateException(
                    "Completed action cannot be cancelled"
            );
        }

        if (status == RiskActionStatus.CANCELLED) {
            throw new InvalidRiskStateException(
                    "Action is already cancelled"
            );
        }

        this.status = RiskActionStatus.CANCELLED;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }

        return value.trim();
    }

    public ProjectId projectId() {
        return projectId;
    }

    public RiskId riskId() {
        return riskId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public Money estimatedCost() {
        return estimatedCost;
    }

    public RiskActionStatus status() {
        return status;
    }
}
