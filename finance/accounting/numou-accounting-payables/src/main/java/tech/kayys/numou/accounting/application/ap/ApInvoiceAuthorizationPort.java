package tech.kayys.numou.accounting.application.ap;

import tech.kayys.numou.accounting.domain.ap.VendorInvoiceScope;

/** Host authorization boundary for company, ledger, period and operation permissions. */
@FunctionalInterface
public interface ApInvoiceAuthorizationPort {
    void authorize(ApRequestContext actor, VendorInvoiceScope scope, ApInvoiceOperation operation);
}
