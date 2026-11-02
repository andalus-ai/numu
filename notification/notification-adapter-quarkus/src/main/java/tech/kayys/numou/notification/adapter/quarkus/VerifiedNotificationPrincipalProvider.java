package tech.kayys.numou.notification.adapter.quarkus;

import java.util.Optional;
import java.util.UUID;

/**
 * Host security bridge. Implementations must read only the verified request
 * security context (for example, Numou CurrentSecurity after RequestSecurityFilter).
 * Never populate this from raw headers, JWT strings, or request DTO fields.
 */
public interface VerifiedNotificationPrincipalProvider {
    Optional<VerifiedPrincipal> current();

    record VerifiedPrincipal(UUID tenantId, UUID principalId) {
        public VerifiedPrincipal {
            java.util.Objects.requireNonNull(tenantId, "tenantId is required");
            java.util.Objects.requireNonNull(principalId, "principalId is required");
        }
    }
}
