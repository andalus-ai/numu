package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.event.AdvanceApplied;
import tech.kayys.syirkah.project.domain.commercial.event.AdvanceCreated;
import tech.kayys.syirkah.project.domain.commercial.event.AdvanceReceived;
import tech.kayys.syirkah.project.domain.commercial.event.AdvanceRefunded;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A contractual advance / down payment (P10.9).
 *
 * The project side owns only entitlement and application of the
 * advance. Receipt of cash is confirmed by Billing/AR (markReceived
 * is driven by that confirmation event), and the advance is later
 * amortized against billing.
 */
public final class ProjectAdvance
        extends AbstractAggregateRoot<ProjectAdvanceId> {

    private final ProjectId projectId;

    private final ProjectContractId contractId;

    private final Money advanceAmount;

    private Money appliedAmount;

    private AdvanceStatus status;

    private ProjectAdvance(
            ProjectAdvanceId id,
            ProjectId projectId,
            ProjectContractId contractId,
            Money advanceAmount
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

        this.advanceAmount = Objects.requireNonNull(
                advanceAmount,
                "advanceAmount cannot be null"
        );

        if (advanceAmount.amount().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Advance amount must be positive"
            );
        }

        this.appliedAmount = Money.zero(advanceAmount.currency());

        this.status = AdvanceStatus.RECEIVABLE;
    }

    public static ProjectAdvance create(
            ProjectAdvanceId id,
            ProjectId projectId,
            ProjectContractId contractId,
            Money advanceAmount
    ) {
        var advance = new ProjectAdvance(
                id,
                projectId,
                contractId,
                advanceAmount
        );

        advance.raise(
                new AdvanceCreated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        projectId,
                        contractId,
                        advanceAmount
                )
        );

        return advance;
    }

    public void markReceived() {
        if (status != AdvanceStatus.RECEIVABLE) {
            throw new InvalidStateException(
                    "Only receivable advance can be marked received"
            );
        }

        status = AdvanceStatus.RECEIVED;

        raise(
                new AdvanceReceived(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        advanceAmount
                )
        );
    }

    public void apply(Money amount) {
        if (status != AdvanceStatus.RECEIVED
                && status != AdvanceStatus.PARTIALLY_APPLIED) {
            throw new InvalidStateException(
                    "Advance cannot be applied from status " + status
            );
        }

        Objects.requireNonNull(amount, "amount cannot be null");

        if (!advanceAmount.currency().equals(amount.currency())) {
            throw new IllegalArgumentException(
                    "Advance currency mismatch"
            );
        }

        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Application amount must be positive"
            );
        }

        Money remaining = remaining();

        if (amount.amount()
                .compareTo(remaining.amount()) > 0) {
            throw new IllegalArgumentException(
                    "Application exceeds remaining advance"
            );
        }

        appliedAmount = appliedAmount.add(amount);

        if (appliedAmount.amount()
                .compareTo(advanceAmount.amount()) == 0) {
            status = AdvanceStatus.FULLY_APPLIED;
        } else {
            status = AdvanceStatus.PARTIALLY_APPLIED;
        }

        raise(
                new AdvanceApplied(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        amount,
                        remaining()
                )
        );
    }

    public void refund() {
        if (status != AdvanceStatus.RECEIVED
                && status != AdvanceStatus.PARTIALLY_APPLIED) {
            throw new InvalidStateException(
                    "Advance cannot be refunded from status " + status
            );
        }

        if (remaining().amount().signum() <= 0) {
            throw new InvalidStateException(
                    "Fully applied advance cannot be refunded"
            );
        }

        status = AdvanceStatus.REFUNDED;

        raise(
                new AdvanceRefunded(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        remaining()
                )
        );
    }

    public void cancel() {
        if (status == AdvanceStatus.FULLY_APPLIED
                || status == AdvanceStatus.REFUNDED) {
            throw new InvalidStateException(
                    "Advance cannot be cancelled"
            );
        }

        status = AdvanceStatus.CANCELLED;
    }

    public Money remaining() {
        return advanceAmount.subtract(appliedAmount);
    }

    public ProjectId projectId() {
        return projectId;
    }

    public ProjectContractId contractId() {
        return contractId;
    }

    public Money advanceAmount() {
        return advanceAmount;
    }

    public Money appliedAmount() {
        return appliedAmount;
    }

    public AdvanceStatus status() {
        return status;
    }
}