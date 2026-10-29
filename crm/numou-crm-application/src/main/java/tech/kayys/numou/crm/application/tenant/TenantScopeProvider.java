package tech.kayys.numou.crm.application.tenant;

/**
 * Application port for resolving the current tenant from a trusted runtime.
 *
 * <p>The adapter implementation must fail closed when no authenticated,
 * verified tenant is available. It must not infer tenant ownership from a
 * CRM command/query or accept an untrusted tenant ID from HTTP input. Jobs
 * and message consumers should resolve scope from an explicitly validated
 * tenant envelope rather than a request ThreadLocal.</p>
 *
 * <p>Adding this port does not by itself enforce isolation: tenant scope must
 * be passed to repository operations and enforced by persistence queries.</p>
 */
@FunctionalInterface
public interface TenantScopeProvider {
    TenantScope requireTenantScope();
}
