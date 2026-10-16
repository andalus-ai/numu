package tech.kayys.syirkah.purchasing.domain.model;

import tech.kayys.syirkah.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.syirkah.foundation.domain.event.DomainEvent;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.purchasing.domain.identifier.ContractId;
import tech.kayys.syirkah.purchasing.domain.identifier.VendorId;
import tech.kayys.syirkah.purchasing.domain.valueobject.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class VendorContract extends AbstractAggregateRoot<ContractId> {

    private static final long serialVersionUID = 1L;

    private String contractNumber;
    private VendorId vendorId;
    private String vendorName;
    private ContractType contractType;
    private String title;
    private String description;
    private Instant effectiveDate;
    private Instant expirationDate;
    private ContractStatus status;
    private Money contractValue;
    private String currencyCode;
    private String termsAndConditions;
    private String notes;
    private String templateId;
    private List<ContractCompliance> complianceRecords;
    private List<ContractPerformance> performanceMetrics;
    private List<ContractAmendment> amendments;
    private String approvedBy;
    private Instant approvedAt;
    private String lastModifiedBy;
    private String legalEntity;
    private String governingLaw;
    private String disputeResolution;
    private boolean autoRenew;
    private int renewalPeriodDays;
    private boolean terminated;
    private Instant terminationDate;
    private String terminationReason;

    public VendorContract() {
        super(ContractId.generate());
        this.complianceRecords = new ArrayList<>();
        this.performanceMetrics = new ArrayList<>();
        this.amendments = new ArrayList<>();
        this.status = ContractStatus.DRAFT;
        this.autoRenew = false;
        this.renewalPeriodDays = 365;
        this.terminated = false;
    }

    public VendorContract(ContractId id) {
        super(id);
        this.complianceRecords = new ArrayList<>();
        this.performanceMetrics = new ArrayList<>();
        this.amendments = new ArrayList<>();
        this.status = ContractStatus.DRAFT;
        this.autoRenew = false;
        this.renewalPeriodDays = 365;
        this.terminated = false;
    }

    public static VendorContract create(ContractId id, String contractNumber, VendorId vendorId,
            String vendorName, ContractType contractType, Instant effectiveDate,
            Instant expirationDate, String currencyCode) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(contractNumber);
        Objects.requireNonNull(vendorId);
        Objects.requireNonNull(vendorName);
        Objects.requireNonNull(contractType);
        Objects.requireNonNull(effectiveDate);
        Objects.requireNonNull(expirationDate);
        Objects.requireNonNull(currencyCode);

        if (expirationDate.isBefore(effectiveDate)) {
            throw new IllegalArgumentException("Expiration date must be after effective date");
        }

        VendorContract contract = new VendorContract(id);
        contract.contractNumber = contractNumber;
        contract.vendorId = vendorId;
        contract.vendorName = vendorName;
        contract.contractType = contractType;
        contract.effectiveDate = effectiveDate;
        contract.expirationDate = expirationDate;
        contract.currencyCode = currencyCode;
        contract.contractValue = Money.zero(currencyCode);
        contract.setCreatedAt(Instant.now());
        contract.addDomainEvent(new ContractCreatedEvent(id, contractNumber, vendorId));
        return contract;
    }

    public void submitForReview() {
        if (status != ContractStatus.DRAFT) {
            throw new IllegalStateException("Only draft contracts can be submitted for review");
        }
        status = ContractStatus.PENDING_REVIEW;
        setUpdatedAt(Instant.now());
        incrementVersion();
        addDomainEvent(new ContractSubmittedForReviewEvent(getId(), contractNumber));
    }

    public void approve(String approvedBy) {
        if (status != ContractStatus.PENDING_APPROVAL && status != ContractStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Contract must be pending approval or review");
        }
        this.approvedBy = approvedBy;
        this.approvedAt = Instant.now();
        status = ContractStatus.PENDING_SIGNATURE;
        setUpdatedAt(Instant.now());
        incrementVersion();
        addDomainEvent(new ContractApprovedEvent(getId(), approvedBy));
    }

    public void activate() {
        if (status != ContractStatus.PENDING_SIGNATURE) {
            throw new IllegalStateException("Contract must be pending signature to activate");
        }
        status = ContractStatus.ACTIVE;
        setUpdatedAt(Instant.now());
        incrementVersion();
        addDomainEvent(new ContractActivatedEvent(getId()));
    }

    public void renew(Instant newExpirationDate) {
        if (status != ContractStatus.ACTIVE && status != ContractStatus.EXPIRED) {
            throw new IllegalStateException("Only active or expired contracts can be renewed");
        }
        this.expirationDate = newExpirationDate;
        if (status == ContractStatus.EXPIRED) {
            status = ContractStatus.RENEWED;
        }
        setUpdatedAt(Instant.now());
        incrementVersion();
        addDomainEvent(new ContractRenewedEvent(getId(), newExpirationDate));
    }

    public void terminate(String reason, String terminatedBy) {
        if (!status.isActive()) {
            throw new IllegalStateException("Only active contracts can be terminated");
        }
        this.terminated = true;
        this.terminationDate = Instant.now();
        this.terminationReason = reason;
        this.lastModifiedBy = terminatedBy;
        status = ContractStatus.TERMINATED;
        setUpdatedAt(Instant.now());
        incrementVersion();
        addDomainEvent(new ContractTerminatedEvent(getId(), reason, terminatedBy));
    }

    public double getComplianceScore() {
        if (complianceRecords.isEmpty()) return 100.0;
        long compliantCount = complianceRecords.stream().filter(ContractCompliance::isCompliant).count();
        return (double) compliantCount / complianceRecords.size() * 100.0;
    }

    public double getPerformanceScore() {
        if (performanceMetrics.isEmpty()) return 0.0;
        return performanceMetrics.stream().mapToDouble(ContractPerformance::getPercentageAchieved).average().orElse(0.0);
    }

    public boolean isActive() { return status.isActive(); }
    public String getContractNumber() { return contractNumber; }
    public VendorId getVendorId() { return vendorId; }
    public String getVendorName() { return vendorName; }
    public ContractType getContractType() { return contractType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Instant getEffectiveDate() { return effectiveDate; }
    public Instant getExpirationDate() { return expirationDate; }
    public ContractStatus getStatus() { return status; }
    public Money getContractValue() { return contractValue; }
    public String getCurrencyCode() { return currencyCode; }
    public String getTermsAndConditions() { return termsAndConditions; }
    public String getNotes() { return notes; }
    public String getTemplateId() { return templateId; }
    public List<ContractCompliance> getComplianceRecords() { return Collections.unmodifiableList(complianceRecords); }
    public List<ContractPerformance> getPerformanceMetrics() { return Collections.unmodifiableList(performanceMetrics); }
    public List<ContractAmendment> getAmendments() { return Collections.unmodifiableList(amendments); }
    public String getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getLastModifiedBy() { return lastModifiedBy; }
    public String getLegalEntity() { return legalEntity; }
    public String getGoverningLaw() { return governingLaw; }
    public String getDisputeResolution() { return disputeResolution; }
    public boolean isAutoRenew() { return autoRenew; }
    public int getRenewalPeriodDays() { return renewalPeriodDays; }
    public boolean isTerminated() { return terminated; }
    public Instant getTerminationDate() { return terminationDate; }
    public String getTerminationReason() { return terminationReason; }

    public void setTitle(String title) { this.title = title; setUpdatedAt(Instant.now()); incrementVersion(); }
    public void setDescription(String description) { this.description = description; setUpdatedAt(Instant.now()); incrementVersion(); }
    public void setContractValue(Money contractValue) { this.contractValue = contractValue; setUpdatedAt(Instant.now()); incrementVersion(); }
    public void setTermsAndConditions(String termsAndConditions) { this.termsAndConditions = termsAndConditions; setUpdatedAt(Instant.now()); incrementVersion(); }
    public void setNotes(String notes) { this.notes = notes; setUpdatedAt(Instant.now()); incrementVersion(); }
    public void setTemplateId(String templateId) { this.templateId = templateId; setUpdatedAt(Instant.now()); incrementVersion(); }
    public void setLegalEntity(String legalEntity) { this.legalEntity = legalEntity; setUpdatedAt(Instant.now()); incrementVersion(); }
    public void setGoverningLaw(String governingLaw) { this.governingLaw = governingLaw; setUpdatedAt(Instant.now()); incrementVersion(); }
    public void setDisputeResolution(String disputeResolution) { this.disputeResolution = disputeResolution; setUpdatedAt(Instant.now()); incrementVersion(); }
    public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; setUpdatedAt(Instant.now()); incrementVersion(); }
    public void setRenewalPeriodDays(int renewalPeriodDays) { this.renewalPeriodDays = renewalPeriodDays; setUpdatedAt(Instant.now()); incrementVersion(); }
    public void setLastModifiedBy(String lastModifiedBy) { this.lastModifiedBy = lastModifiedBy; setUpdatedAt(Instant.now()); incrementVersion(); }

    private void addDomainEvent(DomainEvent event) {
        registerEvent(event);
    }

    public record ContractCreatedEvent(
            UUID eventId,
            Instant occurredAt,
            ContractId contractId,
            String contractNumber,
            VendorId vendorId
    ) implements DomainEvent {
        public ContractCreatedEvent(ContractId contractId, String contractNumber, VendorId vendorId) {
            this(UUID.randomUUID(), Instant.now(), contractId, contractNumber, vendorId);
        }
        @Override
        public String eventType() { return "purchasing.contract.created"; }
        public ContractId getContractId() { return contractId; }
        public String getContractNumber() { return contractNumber; }
        public VendorId getVendorId() { return vendorId; }
    }

    public record ContractSubmittedForReviewEvent(
            UUID eventId,
            Instant occurredAt,
            ContractId contractId,
            String contractNumber
    ) implements DomainEvent {
        public ContractSubmittedForReviewEvent(ContractId contractId, String contractNumber) {
            this(UUID.randomUUID(), Instant.now(), contractId, contractNumber);
        }
        @Override
        public String eventType() { return "purchasing.contract.submitted_for_review"; }
        public ContractId getContractId() { return contractId; }
        public String getContractNumber() { return contractNumber; }
    }

    public record ContractApprovedEvent(
            UUID eventId,
            Instant occurredAt,
            ContractId contractId,
            String approvedBy
    ) implements DomainEvent {
        public ContractApprovedEvent(ContractId contractId, String approvedBy) {
            this(UUID.randomUUID(), Instant.now(), contractId, approvedBy);
        }
        @Override
        public String eventType() { return "purchasing.contract.approved"; }
        public ContractId getContractId() { return contractId; }
        public String getApprovedBy() { return approvedBy; }
    }

    public record ContractActivatedEvent(
            UUID eventId,
            Instant occurredAt,
            ContractId contractId
    ) implements DomainEvent {
        public ContractActivatedEvent(ContractId contractId) {
            this(UUID.randomUUID(), Instant.now(), contractId);
        }
        @Override
        public String eventType() { return "purchasing.contract.activated"; }
        public ContractId getContractId() { return contractId; }
    }

    public record ContractRenewedEvent(
            UUID eventId,
            Instant occurredAt,
            ContractId contractId,
            Instant newExpirationDate
    ) implements DomainEvent {
        public ContractRenewedEvent(ContractId contractId, Instant newExpirationDate) {
            this(UUID.randomUUID(), Instant.now(), contractId, newExpirationDate);
        }
        @Override
        public String eventType() { return "purchasing.contract.renewed"; }
        public ContractId getContractId() { return contractId; }
        public Instant getNewExpirationDate() { return newExpirationDate; }
    }

    public record ContractTerminatedEvent(
            UUID eventId,
            Instant occurredAt,
            ContractId contractId,
            String reason,
            String terminatedBy
    ) implements DomainEvent {
        public ContractTerminatedEvent(ContractId contractId, String reason, String terminatedBy) {
            this(UUID.randomUUID(), Instant.now(), contractId, reason, terminatedBy);
        }
        @Override
        public String eventType() { return "purchasing.contract.terminated"; }
        public ContractId getContractId() { return contractId; }
        public String getReason() { return reason; }
        public String getTerminatedBy() { return terminatedBy; }
    }
}
