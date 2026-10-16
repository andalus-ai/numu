package tech.kayys.syirkah.ecosystem.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.ecosystem.domain.identifier.CapabilityId;
import tech.kayys.syirkah.ecosystem.domain.identifier.EcosystemContractId;
import tech.kayys.syirkah.ecosystem.domain.identifier.ParticipantId;
import tech.kayys.syirkah.ecosystem.domain.valueobject.ContractStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The agreed terms under which a provider supplies a capability to a
 * consumer participant (Docs/Plan/base00.md §6, base01.md §6).
 *
 * <p>Contracts are the reason a relationship can be more than a flag: they
 * carry validity windows and reference the capability being supplied, so
 * fulfilment can pick a provider that is both willing and currently bound.
 */
public final class EcosystemContract extends AbstractAggregateRoot<EcosystemContractId> {

    private static final long serialVersionUID = 1L;

    private ParticipantId providerParticipantId;
    private ParticipantId consumerParticipantId;
    private CapabilityId capabilityId;

    private String reference;
    private ContractStatus status;
    private LocalDate startsOn;
    private LocalDate endsOn;

    private EcosystemContract() {
        super();
    }

    private EcosystemContract(EcosystemContractId id) {
        super(id);
        this.status = ContractStatus.DRAFT;
    }

    /** Drafts a contract between a provider and a consumer of a capability. */
    public static EcosystemContract draft(
            EcosystemContractId id,
            ParticipantId providerParticipantId,
            ParticipantId consumerParticipantId,
            CapabilityId capabilityId,
            String reference,
            LocalDate startsOn,
            LocalDate endsOn) {

        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(providerParticipantId, "providerParticipantId cannot be null");
        Objects.requireNonNull(consumerParticipantId, "consumerParticipantId cannot be null");
        Objects.requireNonNull(capabilityId, "capabilityId cannot be null");

        if (providerParticipantId.equals(consumerParticipantId)) {
            throw new BusinessRuleViolation(
                    "A participant cannot contract with itself for the same capability"
            );
        }
        if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) {
            throw new BusinessRuleViolation("Contract end date cannot precede its start date");
        }

        final var contract = new EcosystemContract(id);
        contract.providerParticipantId = providerParticipantId;
        contract.consumerParticipantId = consumerParticipantId;
        contract.capabilityId = capabilityId;
        contract.reference = requireText(reference, "Contract reference");
        contract.startsOn = startsOn;
        contract.endsOn = endsOn;
        return contract;
    }

    /** Rehydrates a contract from persistence. */
    public static EcosystemContract rehydrate(
            EcosystemContractId id,
            ParticipantId providerParticipantId,
            ParticipantId consumerParticipantId,
            CapabilityId capabilityId,
            String reference,
            ContractStatus status,
            LocalDate startsOn,
            LocalDate endsOn) {

        final var contract = new EcosystemContract(id);
        contract.providerParticipantId = providerParticipantId;
        contract.consumerParticipantId = consumerParticipantId;
        contract.capabilityId = capabilityId;
        contract.reference = reference;
        contract.status = status;
        contract.startsOn = startsOn;
        contract.endsOn = endsOn;
        return contract;
    }

    /** Signs and activates the contract. */
    public void activate() {
        if (status == ContractStatus.TERMINATED) {
            throw new InvalidStateException("A terminated contract cannot be reactivated");
        }
        this.status = ContractStatus.ACTIVE;
        touch();
    }

    /** Marks the contract as expired at the given date. */
    public void expire(LocalDate expiredOn) {
        if (status != ContractStatus.ACTIVE) {
            throw new InvalidStateException(
                    "Only an active contract can expire, was " + status
            );
        }
        this.status = ContractStatus.EXPIRED;
        this.endsOn = expiredOn;
        touch();
    }

    /** Ends the contract before its natural end date. */
    public void terminate(LocalDate terminatedOn) {
        if (status == ContractStatus.TERMINATED) {
            throw new InvalidStateException("Contract is already terminated");
        }
        this.status = ContractStatus.TERMINATED;
        this.endsOn = terminatedOn;
        touch();
    }

    /** True when the contract may be relied upon on the given date. */
    public boolean governsOn(LocalDate date) {
        if (status != ContractStatus.ACTIVE) {
            return false;
        }
        if (date == null) {
            return true;
        }
        final boolean afterStart = startsOn == null || !date.isBefore(startsOn);
        final boolean beforeEnd = endsOn == null || !date.isAfter(endsOn);
        return afterStart && beforeEnd;
    }

    public ParticipantId getProviderParticipantId() {
        return providerParticipantId;
    }

    public ParticipantId getConsumerParticipantId() {
        return consumerParticipantId;
    }

    public CapabilityId getCapabilityId() {
        return capabilityId;
    }

    public String getReference() {
        return reference;
    }

    public ContractStatus getStatus() {
        return status;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    private void touch() {
        setUpdatedAt(Instant.now());
        incrementVersion();
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolation(label + " is required");
        }
        return value.trim();
    }

    @Override
    public String toString() {
        return "EcosystemContract{id=" + getId()
                + ", reference='" + reference + '\''
                + ", provider=" + providerParticipantId
                + ", consumer=" + consumerParticipantId
                + ", status=" + status
                + '}';
    }
}
