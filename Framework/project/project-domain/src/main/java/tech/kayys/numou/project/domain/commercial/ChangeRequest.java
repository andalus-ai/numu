package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.util.Objects;

/**
 * A proposal to change the project contract (P10.9).
 *
 * Change request &#8800; change order: the request is the proposal,
 * the order is the approved contractual instrument. Approving a
 * request never mutates the contract - a ChangeOrder does, and only
 * when applied.
 */
public final class ChangeRequest
        extends AbstractAggregateRoot<ChangeRequestId> {

    private final ProjectId projectId;

    private final ProjectContractId contractId;

    private final ChangeType changeType;

    private final String title;

    private final String description;

    private ChangeRequestStatus status;

    private ChangeRequest(
            ChangeRequestId id,
            ProjectId projectId,
            ProjectContractId contractId,
            ChangeType changeType,
            String title,
            String description
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

        this.changeType = Objects.requireNonNull(
                changeType,
                "changeType cannot be null"
        );

        this.title = requireText(title, "title");

        this.description = requireText(description, "description");

        this.status = ChangeRequestStatus.REQUESTED;
    }

    public static ChangeRequest create(
            ChangeRequestId id,
            ProjectId projectId,
            ProjectContractId contractId,
            ChangeType changeType,
            String title,
            String description
    ) {
        return new ChangeRequest(
                id,
                projectId,
                contractId,
                changeType,
                title,
                description
        );
    }

    public void startReview() {
        requireStatus(ChangeRequestStatus.REQUESTED);

        status = ChangeRequestStatus.UNDER_REVIEW;
    }

    public void assess() {
        requireStatus(ChangeRequestStatus.UNDER_REVIEW);

        status = ChangeRequestStatus.ASSESSED;
    }

    public void approve() {
        requireStatus(ChangeRequestStatus.ASSESSED);

        status = ChangeRequestStatus.APPROVED;
    }

    public void reject() {
        if (status != ChangeRequestStatus.UNDER_REVIEW
                && status != ChangeRequestStatus.ASSESSED) {
            throw new InvalidStateException(
                    "Change request cannot be rejected from " + status
            );
        }

        status = ChangeRequestStatus.REJECTED;
    }

    public void withdraw() {
        if (status == ChangeRequestStatus.APPROVED
                || status == ChangeRequestStatus.REJECTED) {
            throw new InvalidStateException(
                    "Finalized change request cannot be withdrawn"
            );
        }

        status = ChangeRequestStatus.WITHDRAWN;
    }

    private void requireStatus(ChangeRequestStatus expected) {
        if (status != expected) {
            throw new InvalidStateException(
                    "Expected status " + expected + " but was " + status
            );
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    name + " cannot be blank"
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

    public ChangeType changeType() {
        return changeType;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public ChangeRequestStatus status() {
        return status;
    }
}