package tech.kayys.syirkah.purchasing.domain.repository;

import tech.kayys.syirkah.foundation.domain.repository.Repository;
import tech.kayys.syirkah.purchasing.domain.identifier.ContractId;
import tech.kayys.syirkah.purchasing.domain.identifier.VendorId;
import tech.kayys.syirkah.purchasing.domain.model.VendorContract;
import tech.kayys.syirkah.purchasing.domain.valueobject.ContractStatus;
import tech.kayys.syirkah.purchasing.domain.valueobject.ContractType;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Repository for VendorContract aggregates.
 */
public interface VendorContractRepository extends Repository<VendorContract, ContractId> {

    /**
     * Finds contracts by vendor.
     */
    CompletionStage<List<VendorContract>> findByVendorId(VendorId vendorId);

    /**
     * Finds contracts by status.
     */
    CompletionStage<List<VendorContract>> findByStatus(ContractStatus status);

    /**
     * Finds active contracts.
     */
    default CompletionStage<List<VendorContract>> findActiveContracts() {
        return findByStatus(ContractStatus.ACTIVE);
    }

    /**
     * Finds contracts by type.
     */
    CompletionStage<List<VendorContract>> findByType(ContractType type);

    /**
     * Finds contracts expiring between two dates.
     */
    CompletionStage<List<VendorContract>> findExpiringBetween(Instant start, Instant end);

    /**
     * Finds contracts needing renewal (expiring within 30 days).
     */
    default CompletionStage<List<VendorContract>> findContractsNeedingRenewal() {
        Instant now = Instant.now();
        Instant threshold = now.plusSeconds(30L * 24L * 60L * 60L);
        return findExpiringBetween(now, threshold);
    }

    /**
     * Finds contracts with compliance issues.
     */
    CompletionStage<List<VendorContract>> findContractsWithComplianceIssues();

    /**
     * Finds contracts with performance issues.
     */
    CompletionStage<List<VendorContract>> findContractsWithPerformanceIssues();

    /**
     * Finds contracts by vendor and status.
     */
    CompletionStage<List<VendorContract>> findByVendorAndStatus(
        VendorId vendorId,
        ContractStatus status
    );

    /**
     * Finds contracts for renewal processing.
     */
    CompletionStage<List<VendorContract>> findContractsForRenewal(Instant currentDate);

    /**
     * Gets the total contract value by vendor.
     */
    CompletionStage<Money> getTotalContractValueByVendor(VendorId vendorId);

    /**
     * Finds contracts by contract number.
     */
    CompletionStage<VendorContract> findByContractNumber(String contractNumber);

    /**
     * Finds contracts by template ID.
     */
    CompletionStage<List<VendorContract>> findByTemplateId(String templateId);

    /**
     * Counts contracts by status.
     */
    CompletionStage<Long> countByStatus(ContractStatus status);

    /**
     * Finds expired contracts.
     */
    default CompletionStage<List<VendorContract>> findExpiredContracts() {
        return findByStatus(ContractStatus.EXPIRED);
    }

    /**
     * Finds pending contracts (pending review, approval, or signature).
     */
    CompletionStage<List<VendorContract>> findPendingContracts();
}
