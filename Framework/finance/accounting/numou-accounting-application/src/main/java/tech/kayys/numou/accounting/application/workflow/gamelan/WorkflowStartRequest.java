
package tech.kayys.syirkah.accounting.application.workflow.gamelan;

import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

import java.util.Map;
import java.util.Objects;

public record WorkflowStartRequest(
        String processDefinitionKey,
        String businessKey,
        TenantRef tenantId,
        LedgerId ledgerId,
        Map<String, Object> variables
) {
    public WorkflowStartRequest {
        Objects.requireNonNull(processDefinitionKey, "processDefinitionKey cannot be null");
        Objects.requireNonNull(businessKey, "businessKey cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(ledgerId, "ledgerId cannot be null");
    }
}
