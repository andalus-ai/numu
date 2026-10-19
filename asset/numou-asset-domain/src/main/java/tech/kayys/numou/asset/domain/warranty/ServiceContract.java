package tech.kayys.numou.asset.domain.warranty;

import tech.kayys.numou.asset.domain.event.warranty.ServiceContractActivated;
import tech.kayys.numou.asset.domain.event.warranty.ServiceContractCreated;
import tech.kayys.numou.asset.domain.event.warranty.ServiceContractTerminated;
import tech.kayys.numou.foundation.domain.entity.AbstractAggregateRoot;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.foundation.domain.exception.InvalidStateException;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Service contract aggregate: coverage + transactional entitlements (ASSET-23). */
public final class ServiceContract extends AbstractAggregateRoot<ServiceContractId> {

    private static final long serialVersionUID = 1L;

    private String tenantId;
    private UUID assetId;
    private String contractNumber;
    private String providerId;
    private String providerName;
    private ServiceContractType type;
    private ServiceContractStatus status;
    private Instant startsAt;
    private Instant expiresAt;
    private final List<ServiceContractCoverage> coverages = new ArrayList<>();
    private final List<ServiceEntitlement> entitlements = new ArrayList<>();

    private ServiceContract() { super(); }

    private ServiceContract(ServiceContractId id, String tenantId, UUID assetId, String contractNumber) {
        super(id);
        this.tenantId = requireText(tenantId, "tenantId");
        this.assetId = Objects.requireNonNull(assetId, "assetId cannot be null");
        this.contractNumber = requireText(contractNumber, "contractNumber");
        this.status = ServiceContractStatus.DRAFT;
    }

    public static ServiceContract create(ServiceContractId id, String tenantId, UUID assetId, String contractNumber,
                                         String providerId, String providerName, ServiceContractType type,
                                         Instant startsAt, Instant expiresAt, DomainClock clock) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(clock, "clock cannot be null");
        ServiceContract contract = new ServiceContract(id, tenantId, assetId, contractNumber);
        contract.providerId = providerId;
        contract.providerName = providerName;
        contract.type = Objects.requireNonNull(type, "type cannot be null");
        contract.startsAt = Objects.requireNonNull(startsAt, "startsAt cannot be null");
        contract.expiresAt = expiresAt;
        if (expiresAt != null && !expiresAt.isAfter(startsAt)) {
            throw new BusinessRuleViolation("expiresAt must be after startsAt");
        }
        contract.setCreatedAt(clock.now());
        contract.setUpdatedAt(clock.now());
        contract.raise(new ServiceContractCreated(UUID.randomUUID(), clock.now(), id.value(), contractNumber));
        return contract;
    }

    public static ServiceContract reconstitute(ServiceContractId id, String tenantId, UUID assetId,
                                               String contractNumber, String providerId, String providerName,
                                               ServiceContractType type, ServiceContractStatus status,
                                               Instant startsAt, Instant expiresAt,
                                               List<ServiceContractCoverage> coverages,
                                               List<ServiceEntitlement> entitlements) {
        ServiceContract contract = new ServiceContract(id, tenantId, assetId, contractNumber);
        contract.providerId = providerId;
        contract.providerName = providerName;
        contract.type = type;
        contract.status = Objects.requireNonNull(status, "status cannot be null");
        contract.startsAt = startsAt;
        contract.expiresAt = expiresAt;
        if (coverages != null) contract.coverages.addAll(coverages);
        if (entitlements != null) contract.entitlements.addAll(entitlements);
        return contract;
    }

    public void addCoverage(ServiceContractCoverage coverage) {
        coverages.add(Objects.requireNonNull(coverage, "coverage cannot be null"));
    }

    public void addEntitlement(ServiceEntitlement entitlement) {
        Objects.requireNonNull(entitlement, "entitlement cannot be null");
        for (ServiceEntitlement existing : entitlements) {
            if (existing.entitlementCode().equals(entitlement.entitlementCode())) {
                throw new BusinessRuleViolation("entitlement already exists: " + entitlement.entitlementCode());
            }
        }
        entitlements.add(entitlement);
    }

    public void consumeEntitlement(String code, BigDecimal quantity) {
        Objects.requireNonNull(code, "code cannot be null");
        for (ServiceEntitlement entitlement : entitlements) {
            if (entitlement.entitlementCode().equals(code)) {
                entitlement.consume(quantity);
                return;
            }
        }
        throw new BusinessRuleViolation("unknown entitlement: " + code);
    }

    public void activate(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status != ServiceContractStatus.DRAFT && status != ServiceContractStatus.SUSPENDED) {
            throw new InvalidStateException("Contract cannot transition from " + status + " to ACTIVE");
        }
        status = ServiceContractStatus.ACTIVE;
        touch(clock);
        raise(new ServiceContractActivated(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void suspend(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status != ServiceContractStatus.ACTIVE) {
            throw new InvalidStateException("Only ACTIVE contracts can be suspended (was " + status + ")");
        }
        status = ServiceContractStatus.SUSPENDED;
        touch(clock);
    }

    public void terminate(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        if (status == ServiceContractStatus.TERMINATED || status == ServiceContractStatus.EXPIRED) {
            throw new InvalidStateException("Contract is already closed: " + status);
        }
        status = ServiceContractStatus.TERMINATED;
        touch(clock);
        raise(new ServiceContractTerminated(UUID.randomUUID(), clock.now(), id.value()));
    }

    public void expire(DomainClock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");
        status = ServiceContractStatus.EXPIRED;
        touch(clock);
    }

    private void touch(DomainClock clock) {
        setUpdatedAt(clock.now());
        incrementVersion();
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " cannot be null");
        String normalized = value.trim();
        if (normalized.isBlank()) {
            throw new BusinessRuleViolation(field + " cannot be blank");
        }
        return normalized;
    }

    public String tenantId() { return tenantId; }
    public UUID assetId() { return assetId; }
    public String contractNumber() { return contractNumber; }
    public String providerId() { return providerId; }
    public String providerName() { return providerName; }
    public ServiceContractType type() { return type; }
    public ServiceContractStatus status() { return status; }
    public Instant startsAt() { return startsAt; }
    public Instant expiresAt() { return expiresAt; }
    public List<ServiceContractCoverage> coverages() { return Collections.unmodifiableList(coverages); }
    public List<ServiceEntitlement> entitlements() { return Collections.unmodifiableList(entitlements); }
}
