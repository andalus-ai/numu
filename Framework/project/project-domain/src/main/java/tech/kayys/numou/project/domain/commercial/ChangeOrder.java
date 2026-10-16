package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.project.domain.commercial.event.ChangeOrderApproved;
import tech.kayys.syirkah.project.domain.commercial.event.ChangeOrderApplied;
import tech.kayys.syirkah.project.domain.commercial.event.ChangeOrderCancelled;
import tech.kayys.syirkah.project.domain.commercial.event.ChangeOrderCreated;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The approved contractual change instrument (P10.9).
 *
 * Approve &#8800; apply: approval means commercial authority agreed;
 * apply means the change is actually reflected on the contract.
 * The aggregate never reaches into budget, schedule or billing -
 * consumers react to {@link ChangeOrderApplied}.
 */
public final class ChangeOrder
        extends AbstractAggregateRoot<ChangeOrderId> {

    private final ProjectId projectId;

    private final ProjectContractId contractId;

    private final ChangeRequestId changeRequestId;

    private final String number;

    private final String title;

    private final String description;

    private final ChangeImpact impact;

    private ChangeOrderStatus status;

    private Instant approvedAt;

    private Instant appliedAt;

    private ChangeOrder(
            ChangeOrderId id,
            ProjectId projectId,
            ProjectContractId contractId,
            ChangeRequestId changeRequestId,
            String number,
            String title,
            String description,
            ChangeImpact impact
    ) {
        super(id);

        this.projectId = Objects.requireNonNull(
                projectId,
                "projectId cannot be null"
        );

        this.contractId = Objects.requireNonNull(
                contractId,
                "contractId cannot be null"
        );

        this.changeRequestId = changeRequestId;

        this.number = requireText(number, "number");

        this.title = requireText(title, "title");

        this.description = requireText(description, "description");

        this.impact = Objects.requireNonNull(
                impact,
                "impact cannot be null"
        );

        this.status = ChangeOrderStatus.DRAFT;
    }

    public static ChangeOrder create(
            ChangeOrderId id,
            ProjectId projectId,
            ProjectContractId contractId,
            ChangeRequestId changeRequestId,
            String number,
            String title,
            String description,
            ChangeImpact impact
    ) {
        var order = new ChangeOrder(
                id,
                projectId,
                contractId,
                changeRequestId,
                number,
                title,
                description,
                impact
        );

        order.raise(
                new ChangeOrderCreated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        projectId,
                        contractId
                )
        );

        return order;
    }

    public void approve() {
        if (status != ChangeOrderStatus.DRAFT) {
            throw new InvalidStateException(
                    "Only draft change order can be approved"
            );
        }

        status = ChangeOrderStatus.APPROVED;
        approvedAt = Instant.now();

        raise(
                new ChangeOrderApproved(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        contractId,
                        impact.priceDelta(),
                        impact.scheduleDelta(),
                        impact.scopeChanged()
                )
        );
    }

    public void apply() {
        if (status != ChangeOrderStatus.APPROVED) {
            throw new InvalidStateException(
                    "Only approved change order can be applied"
            );
        }

        status = ChangeOrderStatus.APPLIED;
        appliedAt = Instant.now();

        raise(
                new ChangeOrderApplied(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        contractId
                )
        );
    }

    public void cancel() {
        if (status == ChangeOrderStatus.APPLIED) {
            throw new InvalidStateException(
                    "Applied change order cannot be cancelled"
            );
        }

        if (status == ChangeOrderStatus.CANCELLED) {
            throw new InvalidStateException(
                    "Change order is already cancelled"
            );
        }

        status = ChangeOrderStatus.CANCELLED;

        raise(
                new ChangeOrderCancelled(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        contractId
                )
        );
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " cannot be blank"
            );
        }

        return value;
    }

    public ProjectId projectId() {
        return projectId;
    }

    public ProjectContractId contractId() {
        return contractId;
    }

    public ChangeRequestId changeRequestId() {
        return changeRequestId;
    }

    public String number() {
        return number;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public ChangeImpact impact() {
        return impact;
    }

    public ChangeOrderStatus status() {
        return status;
    }

    public Instant approvedAt() {
        return approvedAt;
    }

    public Instant appliedAt() {
        return appliedAt;
    }
}