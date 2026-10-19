package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.compliance.ComplianceRequirement;
import tech.kayys.numou.workforce.domain.compliance.ComplianceRequirementId;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface ComplianceRequirementRepository extends Repository<ComplianceRequirement, ComplianceRequirementId> {
    CompletionStage<Optional<ComplianceRequirement>> findByTenantAndCode(TenantId tenantId, String code);
    CompletionStage<List<ComplianceRequirement>> findActiveByTenant(TenantId tenantId);
}
