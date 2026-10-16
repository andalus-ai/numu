package tech.kayys.syirkah.construction.domain.contract;

import tech.kayys.syirkah.construction.domain.contract.event.ConstructionContractActivated;
import tech.kayys.syirkah.construction.domain.contract.event.ConstructionContractCreated;
import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ConstructionContract extends AbstractAggregateRoot<ConstructionContractId> {
    private final UUID projectId;
    private final String contractNumber;
    private String title;
    private ContractParty client;
    private ContractParty contractor;
    private ContractValue originalValue;
    private ContractStatus status;

    private ConstructionContract(
            ConstructionContractId id,
            UUID projectId,
            String contractNumber,
            String title,
            ContractParty client,
            ContractParty contractor,
            ContractValue originalValue
    ) {
        super(id);
        this.projectId = Objects.requireNonNull(projectId, "Project id cannot be null");
        this.contractNumber = Objects.requireNonNull(contractNumber, "Contract number cannot be null");
        this.title = Objects.requireNonNull(title, "Title cannot be blank");
        this.client = Objects.requireNonNull(client, "Client party cannot be null");
        this.contractor = Objects.requireNonNull(contractor, "Contractor party cannot be null");
        this.originalValue = Objects.requireNonNull(originalValue, "Original value cannot be null");
        this.status = ContractStatus.DRAFT;
    }

    public static ConstructionContract create(
            UUID projectId,
            String contractNumber,
            String title,
            ContractParty client,
            ContractParty contractor,
            ContractValue originalValue
    ) {
        var contract = new ConstructionContract(ConstructionContractId.generate(), projectId, contractNumber, title, client, contractor, originalValue);
        contract.raise(new ConstructionContractCreated(UUID.randomUUID(), Instant.now(), contract.id().value(), projectId, contractNumber));
        return contract;
    }

    public void activate() {
        if (status != ContractStatus.DRAFT && status != ContractStatus.SUSPENDED) {
            throw new IllegalStateException("Contract cannot be activated from " + status);
        }
        status = ContractStatus.ACTIVE;
        raise(new ConstructionContractActivated(UUID.randomUUID(), Instant.now(), id().value(), projectId));
    }

    public void close() {
        status = ContractStatus.CLOSED;
    }

    public UUID projectId() { return projectId; }
    public String contractNumber() { return contractNumber; }
    public String title() { return title; }
    public ContractParty client() { return client; }
    public ContractParty contractor() { return contractor; }
    public ContractValue originalValue() { return originalValue; }
    public ContractStatus status() { return status; }
}
