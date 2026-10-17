package tech.kayys.numou.accounting.application.port;

import tech.kayys.numou.accounting.domain.islamic.SyirkahBusinessResult;
import tech.kayys.numou.accounting.domain.islamic.SyirkahCalculation;
import tech.kayys.numou.accounting.domain.islamic.SyirkahContractVersion;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Persistence port for Syirkah. Implementations must make each write atomic and
 * scope every query by tenant and company.
 */
public interface SyirkahRepository {
    void createContract(SyirkahContractVersion contract, String createdBy);
    void createContractVersion(SyirkahContractVersion contract, String createdBy);
    void activateContract(String tenantId, String companyId, String contractId, int version,
                          String actor, String signedEvidenceReference);
    void rejectContractVersion(String tenantId, String companyId, String contractId, int version,
                               String actor, String reason);
    boolean isContractActive(String tenantId, String companyId, String contractId);
    boolean isContractVersionActivated(String tenantId, String companyId, String contractId, int version);
    Optional<SyirkahContractVersion> findContract(String tenantId, String companyId,
                                                   String contractId, int version);

    void saveBusinessResult(String tenantId, String companyId, SyirkahBusinessResult result, String createdBy);
    Optional<SyirkahBusinessResult> findBusinessResult(String tenantId, String companyId, String resultId);

    int saveCalculation(String tenantId, String companyId, SyirkahCalculation calculation, String calculatedBy);
    Optional<SyirkahCalculation> findCalculation(String tenantId, String companyId,
                                                  String resultId, int calculationVersion);
    void approveCalculation(String tenantId, String companyId, String resultId,
                            int calculationVersion, String approver);

    void settleDistribution(String tenantId, String companyId, String resultId, int calculationVersion,
                            String partyId, BigDecimal amount, String currencyCode,
                            String idempotencyKey, String actor);
}
