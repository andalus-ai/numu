
package tech.kayys.syirkah.accounting.application.rule;

import tech.kayys.syirkah.accounting.domain.ledger.LedgerId;
import tech.kayys.syirkah.accounting.domain.multitenancy.TenantRef;

/**
 * Marker and base context for evaluating financial rules.
 */
public interface RuleContext {
    TenantRef tenantId();
    LedgerId ledgerId();
}
