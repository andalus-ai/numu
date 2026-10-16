
package tech.kayys.numou.accounting.application.rule;

import tech.kayys.numou.accounting.domain.ledger.LedgerId;
import tech.kayys.numou.accounting.domain.multitenancy.TenantRef;

/**
 * Marker and base context for evaluating financial rules.
 */
public interface RuleContext {
    TenantRef tenantId();
    LedgerId ledgerId();
}
