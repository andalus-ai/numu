package tech.kayys.numou.accounting.interfaces.authorization;

import jakarta.enterprise.context.ApplicationScoped;
import tech.kayys.numou.accounting.application.ap.ApInvoiceAuthorizationPort;
import tech.kayys.numou.accounting.application.ap.ApInvoiceOperation;
import tech.kayys.numou.accounting.application.ap.ApRequestContext;
import tech.kayys.numou.accounting.domain.ap.VendorInvoiceScope;

/**
 * Fail-closed composition default. A deployment must replace this bean with
 * its identity/policy-backed AP authorization adapter before enabling AP writes.
 */
@ApplicationScoped
public class DenyByDefaultApInvoiceAuthorizationAdapter implements ApInvoiceAuthorizationPort {
    @Override
    public void authorize(ApRequestContext actor, VendorInvoiceScope scope, ApInvoiceOperation operation) {
        throw new SecurityException("AP authorization policy is not configured for this deployment");
    }
}
