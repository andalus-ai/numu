package tech.kayys.numou.workforce.application.command;

import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.compliance.ComplianceRequirementType;

import java.time.LocalDate;
import java.util.Objects;

public record CreateComplianceRequirementCommand(
        TenantId tenantId,
        String code,
        String name,
        String description,
        ComplianceRequirementType type,
        LocalDate effectiveFrom,
        LocalDate effectiveTo
) implements Command {
    public CreateComplianceRequirementCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
    }
}
