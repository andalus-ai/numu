package tech.kayys.payment.adapter.postgres;

/**
 * Host-provided authorization boundary for payment operations.
 *
 * <p>The implementation must derive the authenticated principal from the
 * host's trusted security context and verify that principal may act for the
 * requested tenant. It must not trust a tenant identifier supplied by an
 * untrusted HTTP request on its own.</p>
 */
@FunctionalInterface
public interface PaymentTenantAuthorizationPort {
    void authorizeTenant(String tenantId);
}
