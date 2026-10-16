package tech.kayys.numou.accounting.application.islamic;

import tech.kayys.numou.accounting.application.port.SyirkahRepository;
import tech.kayys.numou.accounting.domain.islamic.SyirkahBusinessResult;
import tech.kayys.numou.accounting.domain.islamic.SyirkahCalculation;
import tech.kayys.numou.accounting.domain.islamic.SyirkahCalculator;
import tech.kayys.numou.accounting.domain.islamic.SyirkahContractVersion;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Coordinates Syirkah domain operations. Tenant authorization is enforced at the
 * adapter boundary; this service independently rejects cross-tenant contract terms.
 */
public final class SyirkahApplicationService {
    private final SyirkahRepository repository;
    private final SyirkahCalculator calculator;

    public SyirkahApplicationService(SyirkahRepository repository, SyirkahCalculator calculator) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
    }

    public void createContract(SyirkahContractVersion contract, String tenantId, String actor) {
        requireText(tenantId, "tenantId");
        requireText(actor, "actor");
        if (!contract.tenantId().equals(tenantId)) {
            throw new SecurityException("Contract tenant does not match authenticated tenant");
        }
        repository.createContract(contract, actor);
    }

    public void createContractVersion(SyirkahContractVersion contract, String tenantId, String actor) {
        requireText(tenantId, "tenantId");
        requireText(actor, "actor");
        if (!contract.tenantId().equals(tenantId)) {
            throw new SecurityException("Contract tenant does not match authenticated tenant");
        }
        if (!repository.isContractActive(tenantId, contract.companyId(), contract.contractId())) {
            throw new IllegalStateException("Only an active contract can be amended");
        }
        repository.createContractVersion(contract, actor);
    }

    public void activateContract(String tenantId, String companyId, String contractId,
                                 int version, String actor, String signedEvidenceReference) {
        requireText(actor, "actor");
        requireText(signedEvidenceReference, "signedEvidenceReference");
        repository.activateContract(tenantId, companyId, contractId, version, actor, signedEvidenceReference);
    }

    public void registerBusinessResult(String tenantId, String companyId,
                                       SyirkahBusinessResult result, String actor) {
        requireText(actor, "actor");
        if (!repository.isContractActive(tenantId, companyId, result.contractId())) {
            throw new IllegalStateException("Syirkah contract must be active before results can be registered");
        }
        SyirkahContractVersion contract = repository.findContract(tenantId, companyId,
                result.contractId(), result.contractVersion())
                .orElseThrow(() -> new IllegalArgumentException("Contract version not found in this tenant/company"));
        if (!repository.isContractVersionActivated(tenantId, companyId, result.contractId(), result.contractVersion())) {
            throw new IllegalStateException("A draft contract version cannot be used for business results");
        }
        if (!contract.tenantId().equals(tenantId) || !contract.companyId().equals(companyId)) {
            throw new SecurityException("Contract scope mismatch");
        }
        // Record the evidenced business result even when its loss-policy subtype needs
        // specialist review. Policy evaluation belongs to the explicit calculate step.
        if (!contract.currencyCode().equalsIgnoreCase(result.currencyCode())) {
            throw new IllegalArgumentException("Business result currency differs from contract currency");
        }
        if (result.periodStart().isBefore(contract.effectiveFrom())
                || result.periodEnd().isAfter(contract.effectiveUntil())) {
            throw new IllegalArgumentException("Business period falls outside contract version effective dates");
        }
        repository.saveBusinessResult(tenantId, companyId, result, actor);
    }

    public SyirkahCalculation calculate(String tenantId, String companyId, String resultId, String actor) {
        requireText(actor, "actor");
        SyirkahBusinessResult result = repository.findBusinessResult(tenantId, companyId, resultId)
                .orElseThrow(() -> new IllegalArgumentException("Business result not found"));
        if (!repository.isContractActive(tenantId, companyId, result.contractId())) {
            throw new IllegalStateException("Syirkah contract is not active");
        }
        SyirkahContractVersion contract = repository.findContract(tenantId, companyId,
                result.contractId(), result.contractVersion())
                .orElseThrow(() -> new IllegalArgumentException("Contract version not found"));
        if (!repository.isContractVersionActivated(tenantId, companyId, result.contractId(), result.contractVersion())) {
            throw new IllegalStateException("A draft contract version cannot be calculated");
        }
        SyirkahCalculation calculation = calculator.calculate(contract, result);
        int version = repository.saveCalculation(tenantId, companyId, calculation, actor);
        return repository.findCalculation(tenantId, companyId, resultId, version)
                .orElseThrow(() -> new IllegalStateException("Persisted calculation could not be reloaded"));
    }

    public SyirkahCalculation findCalculation(String tenantId, String companyId, String resultId,
                                             int calculationVersion) {
        return repository.findCalculation(tenantId, companyId, resultId, calculationVersion)
                .orElseThrow(() -> new IllegalArgumentException("Calculation snapshot not found"));
    }

    public SyirkahCalculation approveCalculation(String tenantId, String companyId, String resultId,
                                                int calculationVersion, String approver) {
        requireText(approver, "approver");
        repository.approveCalculation(tenantId, companyId, resultId, calculationVersion, approver);
        return repository.findCalculation(tenantId, companyId, resultId, calculationVersion)
                .orElseThrow(() -> new IllegalStateException("Approved calculation could not be reloaded"));
    }

    public void settleDistribution(String tenantId, String companyId, String resultId, int calculationVersion,
                                  String partyId, BigDecimal amount, String currencyCode,
                                  String idempotencyKey, String actor) {
        requireText(actor, "actor");
        requireText(idempotencyKey, "idempotencyKey");
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Settlement amount must be positive");
        }
        repository.settleDistribution(tenantId, companyId, resultId, calculationVersion,
                partyId, amount, currencyCode, idempotencyKey, actor);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
