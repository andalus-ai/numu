package tech.kayys.syirkah.project.domain.commercial;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.foundation.domain.valueobject.DateRange;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.project.domain.commercial.event.ProjectContractActivated;
import tech.kayys.syirkah.project.domain.commercial.event.ProjectContractApproved;
import tech.kayys.syirkah.project.domain.commercial.event.ProjectContractCompleted;
import tech.kayys.syirkah.project.domain.commercial.event.ProjectContractCreated;
import tech.kayys.syirkah.project.domain.project.ProjectId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The commercial contract of a project (P10.9 core aggregate).
 *
 * The aggregate guards the invariants that must stay atomic: status
 * lifecycle, party uniqueness and value consistency. It never touches
 * budgets, schedules or invoices - downstream domains react to its
 * events instead.
 */
public final class ProjectContract
        extends AbstractAggregateRoot<ProjectContractId> {

    private final ProjectId projectId;

    private final ContractType contractType;

    private final DateRange contractPeriod;

    private ContractStatus status;

    private ContractValue contractValue;

    private final List<ContractParty> parties = new ArrayList<>();

    private ProjectContract(
            ProjectContractId id,
            ProjectId projectId,
            ContractType contractType,
            DateRange contractPeriod,
            Money contractValue
    ) {
        super(id);

        this.projectId = Objects.requireNonNull(
                projectId,
                "projectId cannot be null"
        );

        this.contractType = Objects.requireNonNull(
                contractType,
                "contractType cannot be null"
        );

        this.contractPeriod = Objects.requireNonNull(
                contractPeriod,
                "contractPeriod cannot be null"
        );

        this.contractValue = ContractValue.initial(
                Objects.requireNonNull(
                        contractValue,
                        "contractValue cannot be null"
                )
        );

        this.status = ContractStatus.DRAFT;
    }

    public static ProjectContract create(
            ProjectContractId id,
            ProjectId projectId,
            ContractType contractType,
            DateRange contractPeriod,
            Money contractValue
    ) {
        if (contractValue.amount().signum() < 0) {
            throw new IllegalArgumentException(
                    "Contract value cannot be negative"
            );
        }

        var contract = new ProjectContract(
                id,
                projectId,
                contractType,
                contractPeriod,
                contractValue
        );

        contract.raise(
                new ProjectContractCreated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id,
                        projectId,
                        contractType
                )
        );

        return contract;
    }

    public void submitForReview() {
        requireStatus(ContractStatus.DRAFT);

        status = ContractStatus.UNDER_REVIEW;
    }

    public void addParty(ContractParty party) {
        requireEditable();

        Objects.requireNonNull(party, "party cannot be null");

        boolean exists = parties.stream()
                .anyMatch(existing ->
                        existing.partyId().equals(party.partyId())
                                && existing.role().equals(party.role()));

        if (exists) {
            throw new IllegalStateException(
                    "Contract party already exists"
            );
        }

        parties.add(party);
    }

    public void approve() {
        if (status != ContractStatus.DRAFT
                && status != ContractStatus.UNDER_REVIEW) {
            throw new InvalidStateException(
                    "Contract cannot be approved from status " + status
            );
        }

        status = ContractStatus.APPROVED;

        raise(
                new ProjectContractApproved(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId
                )
        );
    }

    public void activate() {
        if (status != ContractStatus.APPROVED) {
            throw new InvalidStateException(
                    "Contract must be approved before activation"
            );
        }

        status = ContractStatus.ACTIVE;

        raise(
                new ProjectContractActivated(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId
                )
        );
    }

    public void suspend() {
        if (status != ContractStatus.ACTIVE) {
            throw new InvalidStateException(
                    "Only active contract can be suspended"
            );
        }

        status = ContractStatus.SUSPENDED;
    }

    public void resume() {
        if (status != ContractStatus.SUSPENDED) {
            throw new InvalidStateException(
                    "Only suspended contract can be resumed"
            );
        }

        status = ContractStatus.ACTIVE;
    }

    public void complete() {
        if (status != ContractStatus.ACTIVE) {
            throw new InvalidStateException(
                    "Only active contract can be completed"
            );
        }

        status = ContractStatus.COMPLETED;

        raise(
                new ProjectContractCompleted(
                        UUID.randomUUID(),
                        Instant.now(),
                        id(),
                        projectId
                )
        );
    }

    public void terminate() {
        if (status != ContractStatus.ACTIVE
                && status != ContractStatus.SUSPENDED) {
            throw new InvalidStateException(
                    "Contract cannot be terminated from status " + status
            );
        }

        status = ContractStatus.TERMINATED;
    }

    public void cancel() {
        if (status != ContractStatus.DRAFT
                && status != ContractStatus.UNDER_REVIEW) {
            throw new InvalidStateException(
                    "Contract cannot be cancelled from status " + status
            );
        }

        status = ContractStatus.CANCELLED;
    }

    /**
     * Adjusts the current value while preserving the original
     * baseline. In production this is driven by applied change
     * orders through a commercial policy, not called ad hoc.
     */
    public void changeValue(Money newValue) {
        requireEditableOrActive();

        Objects.requireNonNull(newValue, "newValue cannot be null");

        if (!newValue.currency()
                .equals(contractValue.current().currency())) {
            throw new IllegalArgumentException(
                    "Contract currency cannot change"
            );
        }

        if (newValue.amount().signum() < 0) {
            throw new IllegalArgumentException(
                    "Contract value cannot be negative"
            );
        }

        contractValue = new ContractValue(
                contractValue.original(),
                newValue
        );
    }

    private void requireEditable() {
        if (status != ContractStatus.DRAFT
                && status != ContractStatus.UNDER_REVIEW) {
            throw new InvalidStateException(
                    "Contract is not editable in status " + status
            );
        }
    }

    private void requireEditableOrActive() {
        if (status != ContractStatus.DRAFT
                && status != ContractStatus.UNDER_REVIEW
                && status != ContractStatus.ACTIVE) {
            throw new InvalidStateException(
                    "Contract cannot be modified in status " + status
            );
        }
    }

    private void requireStatus(ContractStatus expected) {
        if (status != expected) {
            throw new InvalidStateException(
                    "Contract must be " + expected + " but was " + status
            );
        }
    }

    public ProjectId projectId() {
        return projectId;
    }

    public ContractType contractType() {
        return contractType;
    }

    public ContractStatus status() {
        return status;
    }

    public DateRange contractPeriod() {
        return contractPeriod;
    }

    public ContractValue contractValue() {
        return contractValue;
    }

    public List<ContractParty> parties() {
        return List.copyOf(parties);
    }
}