package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.event.RetentionForfeited;
import tech.kayys.syirkah.project.domain.commercial.event.RetentionHeld;
import tech.kayys.syirkah.project.domain.commercial.event.RetentionReleased;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A contractual holdback (P10.9).
 *
 * Retention is not a discount - it is money withheld until
 * contractual conditions are met, then released (partially or
 * fully) or forfeited.
 */
public final class ProjectRetention
        extends AbstractAggregateRoot<ProjectRetentionId> {

    private final ProjectId projectId;

    private final ProjectContractId contractId;

    private final Money heldAmount;

    private Money releasedAmount;

    private RetentionStatus status;

    private ProjectRetention(
            ProjectRetentionId id,
            ProjectId projectId,
            ProjectContractId contractId,
            Money heldAmount
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

        this.heldAmount = Objects.requireNonNull(
                heldAmount,
                "heldAmount cannot be null"
        );

        if (heldAmount.amount().signum() < 0) {
            throw new IllegalArgumentException(
                    "Retention cannot be negative"
            );
        }

        this.releasedAmount = Money.zero(heldAmount.currency());

        this.status = RetentionStatus.HELD;
    }

    public static ProjectRetention create(
            ProjectRetentionId id,
            ProjectId projectId,
            ProjectContractId contractId,
            Money heldAmount
    ) {
        var retention = new ProjectRetention(
                id,
                projectId,
                contractId,
                heldAmount
        );

        retention.raise(
                new RetentionHeld(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        projectId,
                        heldAmount
                )
        );

        return retention;
    }

    public void release(Money amount) {
        if (status == RetentionStatus.FULLY_RELEASED
                || status == RetentionStatus.FORFEITED
                || status == RetentionStatus.CANCELLED) {
            throw new InvalidStateException(
                    "Retention cannot be released from status " + status
            );
        }

        Objects.requireNonNull(amount, "amount cannot be null");
        validateCurrency(amount);

        Money remaining = remaining();

        if (amount.amount().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Release amount must be positive"
            );
        }

        if (amount.amount()
                .compareTo(remaining.amount()) > 0) {
            throw new IllegalArgumentException(
                    "Release amount exceeds remaining retention"
            );
        }

        releasedAmount = releasedAmount.add(amount);

        if (releasedAmount.amount()
                .compareTo(heldAmount.amount()) == 0) {
            status = RetentionStatus.FULLY_RELEASED;
        } else {
            status = RetentionStatus.PARTIALLY_RELEASED;
        }

        raise(
                new RetentionReleased(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        amount,
                        remaining()
                )
        );
    }

    public void forfeit() {
        if (status == RetentionStatus.FULLY_RELEASED
                || status == RetentionStatus.CANCELLED) {
            throw new InvalidStateException(
                    "Retention cannot be forfeited"
            );
        }

        status = RetentionStatus.FORFEITED;

        raise(
                new RetentionForfeited(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        remaining()
                )
        );
    }

    public void cancel() {
        if (status == RetentionStatus.FULLY_RELEASED
                || status == RetentionStatus.FORFEITED) {
            throw new InvalidStateException(
                    "Retention cannot be cancelled"
            );
        }

        status = RetentionStatus.CANCELLED;
    }

    public Money remaining() {
        return heldAmount.subtract(releasedAmount);
    }

    private void validateCurrency(Money amount) {
        if (!heldAmount.currency().equals(amount.currency())) {
            throw new IllegalArgumentException(
                    "Retention currency mismatch"
            );
        }
    }

    public ProjectId projectId() {
        return projectId;
    }

    public ProjectContractId contractId() {
        return contractId;
    }

    public Money heldAmount() {
        return heldAmount;
    }

    public Money releasedAmount() {
        return releasedAmount;
    }

    public RetentionStatus status() {
        return status;
    }
}