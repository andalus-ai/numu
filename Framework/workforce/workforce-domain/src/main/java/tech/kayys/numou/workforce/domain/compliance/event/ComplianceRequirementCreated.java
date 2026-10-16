package tech.kayys.numou.workforce.domain.compliance.event;

import tech.kayys.numou.foundation.domain.event.DomainEvent;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.compliance.ComplianceRequirementId;
import tech.kayys.numou.workforce.domain.compliance.ComplianceRequirementType;

import java.time.Instant;
import java.util.UUID;

public record ComplianceRequirementCreated(
        UUID eventId,
        Instant occurredAt,
        ComplianceRequirementId id,
        TenantId tenantId,
        String code,
        ComplianceRequirementType type
) implements DomainEvent {
    public ComplianceRequirementCreated(ComplianceRequirementId id, TenantId tenantId, String code, ComplianceRequirementType type) {
        this(UUID.randomUUID(), Instant.now(), id, tenantId, code, type);
    }

    @Override
    public String eventType() {
        return "workforce.compliance.requirement.created";
    }
}
