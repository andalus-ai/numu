package tech.kayys.numou.security.application.context;

import tech.kayys.numou.security.domain.authorization.Principal;

import java.util.UUID;

/**
 * Decides which tenant a request acts in.
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>A tenant claim inside the validated token is authoritative. A client
 *       header naming a <em>different</em> tenant is rejected, not ignored -
 *       a mismatch is either a bug or an attack and should be visible.</li>
 *   <li>With no token claim, the header is accepted only if the principal is a
 *       member of that tenant (multi-tenant users picking a workspace).</li>
 *   <li>With neither, a principal that belongs to exactly one tenant acts in it;
 *       anything else is rejected rather than guessed.</li>
 * </ol>
 */
public final class TenantGuard {

    private TenantGuard() { }

    public static TenantResolution resolve(Principal principal, UUID tokenTenantClaim, String headerTenant) {
        final UUID header;
        try {
            header = headerTenant == null || headerTenant.isBlank() ? null : UUID.fromString(headerTenant.trim());
        } catch (IllegalArgumentException e) {
            return new TenantResolution.Rejected("malformed tenant header");
        }

        if (tokenTenantClaim != null) {
            if (!principal.isMemberOf(tokenTenantClaim) && !principal.tenantIds().isEmpty()) {
                return new TenantResolution.Rejected("token tenant claim not in principal memberships");
            }
            if (header != null && !header.equals(tokenTenantClaim)) {
                return new TenantResolution.Rejected("tenant header does not match token tenant");
            }
            return new TenantResolution.Resolved(tokenTenantClaim);
        }
        if (header != null) {
            return principal.isMemberOf(header)
                    ? new TenantResolution.Resolved(header)
                    : new TenantResolution.Rejected("principal is not a member of requested tenant");
        }
        if (principal.tenantIds().size() == 1) {
            return new TenantResolution.Resolved(principal.tenantIds().iterator().next().value());
        }
        return new TenantResolution.Rejected("tenant not specified and not inferable");
    }
}
