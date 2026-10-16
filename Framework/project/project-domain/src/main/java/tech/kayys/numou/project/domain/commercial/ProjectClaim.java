package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.event.ClaimAccepted;
import tech.kayys.syirkah.project.domain.commercial.event.ClaimRejected;
import tech.kayys.syirkah.project.domain.commercial.event.ClaimSettled;
import tech.kayys.syirkah.project.domain.commercial.event.ClaimSubmitted;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An assertion of contractual/commercial entitlement (P10.9).
 *
 * A claim is neither a change request nor an invoice - it asserts an
 * entitlement, which is reviewed and possibly settled. Settlement
 * effects on receivables happen downstream, driven by events.
 */
public final class ProjectClaim
        extends AbstractAggregateRoot<ProjectClaimId> {

    private final ProjectId projectId;

    private final ProjectContractId contractId;

    private final ClaimType type;

    private final String number;

    private final String title;

    private final String description;

    private final Money claimedAmount;

    private Money acceptedAmount;

    private ClaimStatus status;

    private ProjectClaim(
            ProjectClaimId id,
            ProjectId projectId,
            ProjectContractId contractId,
            ClaimType type,
            String number,
            String title,
            String description,
            Money claimedAmount
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

        this.type = Objects.requireNonNull(type, "type cannot be null");

        this.number = requireText(number, "number");

        this.title = requireText(title, "title");

        this.description = requireText(description, "description");

        this.claimedAmount = Objects.requireNonNull(
                claimedAmount,
                "claimedAmount cannot be null"
        );

        if (claimedAmount.amount().signum() < 0) {
            throw new IllegalArgumentException(
                    "Claimed amount cannot be negative"
            );
        }

        this.acceptedAmount = Money.zero(claimedAmount.currency());

        this.status = ClaimStatus.DRAFT;
    }

    public static ProjectClaim create(
            ProjectClaimId id,
            ProjectId projectId,
            ProjectContractId contractId,
            ClaimType type,
            String number,
            String title,
            String description,
            Money claimedAmount
    ) {
        return new ProjectClaim(
                id,
                projectId,
                contractId,
                type,
                number,
                title,
                description,
                claimedAmount
        );
    }

    public void submit() {
        requireStatus(ClaimStatus.DRAFT);

        status = ClaimStatus.SUBMITTED;

        raise(
                new ClaimSubmitted(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        contractId
                )
        );
    }

    public void startReview() {
        requireStatus(ClaimStatus.SUBMITTED);

        status = ClaimStatus.UNDER_REVIEW;
    }

    public void accept(Money amount) {
        requireReviewState();

        validateAcceptedAmount(amount);

        acceptedAmount = amount;
        status = ClaimStatus.ACCEPTED;

        raise(
                new ClaimAccepted(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        claimedAmount,
                        acceptedAmount
                )
        );
    }

    public void partiallyAccept(Money amount) {
        requireReviewState();

        validateAcceptedAmount(amount);

        if (amount.amount()
                .compareTo(claimedAmount.amount()) >= 0) {
            throw new IllegalArgumentException(
                    "Partial acceptance must be less than claimed amount"
            );
        }

        acceptedAmount = amount;
        status = ClaimStatus.PARTIALLY_ACCEPTED;

        raise(
                new ClaimAccepted(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        claimedAmount,
                        acceptedAmount
                )
        );
    }

    public void reject() {
        requireReviewState();

        status = ClaimStatus.REJECTED;

        raise(
                new ClaimRejected(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId
                )
        );
    }

    public void settle() {
        if (status != ClaimStatus.ACCEPTED
                && status != ClaimStatus.PARTIALLY_ACCEPTED) {
            throw new InvalidStateException(
                    "Only accepted claim can be settled"
            );
        }

        status = ClaimStatus.SETTLED;

        raise(
                new ClaimSettled(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId,
                        acceptedAmount
                )
        );
    }

    public void withdraw() {
        if (status == ClaimStatus.SETTLED
                || status == ClaimStatus.ACCEPTED
                || status == ClaimStatus.PARTIALLY_ACCEPTED) {
            throw new InvalidStateException(
                    "Finalized claim cannot be withdrawn"
            );
        }

        status = ClaimStatus.WITHDRAWN;
    }

    private void requireReviewState() {
        if (status != ClaimStatus.UNDER_REVIEW) {
            throw new InvalidStateException(
                    "Claim must be under review"
            );
        }
    }

    private void validateAcceptedAmount(Money amount) {
        Objects.requireNonNull(amount, "amount cannot be null");

        if (!amount.currency()
                .equals(claimedAmount.currency())) {
            throw new IllegalArgumentException(
                    "Claim currency mismatch"
            );
        }

        if (amount.amount().signum() < 0) {
            throw new IllegalArgumentException(
                    "Accepted amount cannot be negative"
            );
        }

        if (amount.amount()
                .compareTo(claimedAmount.amount()) > 0) {
            throw new IllegalArgumentException(
                    "Accepted amount cannot exceed claimed amount"
            );
        }
    }

    private void requireStatus(ClaimStatus expected) {
        if (status != expected) {
            throw new InvalidStateException(
                    "Expected claim status " + expected
                            + " but was " + status
            );
        }
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

    public ClaimType type() {
        return type;
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

    public Money claimedAmount() {
        return claimedAmount;
    }

    public Money acceptedAmount() {
        return acceptedAmount;
    }

    public ClaimStatus status() {
        return status;
    }
}