package tech.kayys.numou.transport.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.security.domain.authorization.AccessDecision;
import tech.kayys.numou.security.domain.authorization.AccessRequest;
import tech.kayys.numou.security.domain.authorization.Principal;
import tech.kayys.numou.security.domain.identifier.PrincipalId;
import tech.kayys.numou.security.domain.valueobject.ActionType;
import tech.kayys.numou.security.spi.port.AuthorizationServicePort;
import tech.kayys.numou.security.spi.port.TenantMembershipResolver;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Enforces authoritative tenant membership and capability-level permission before
 * a booking command reaches the Transport application service.
 *
 * <p>This adapter depends only on the public Security SPI. It deliberately does
 * not depend on identity-provider storage or assume that a verified tenant claim
 * by itself proves active membership.</p>
 */
@ApplicationScoped
public class TransportBookingAuthorization {
    @Inject
    TenantMembershipResolver memberships;

    @Inject
    AuthorizationServicePort authorization;

    @ConfigProperty(name = "numou.transport.authorization.timeout-millis", defaultValue = "2000")
    long timeoutMillis;

    public void requireAllowed(TransportIdentityContext caller, String principalName,
                               BookingOperation operation, UUID departureId, UUID holdId) {
        if (caller == null || principalName == null || principalName.isBlank()) {
            throw new SecurityException("verified caller identity is required");
        }
        if (timeoutMillis < 100 || timeoutMillis > 10_000) {
            throw new TransportAuthorizationUnavailableException("authorization timeout configuration is invalid");
        }

        final Principal principal;
        final TenantId tenant;
        try {
            principal = new Principal(PrincipalId.fromString(caller.actorId()), principalName, Set.of());
            tenant = TenantId.of(caller.tenantId());
        } catch (RuntimeException invalidIdentity) {
            throw new SecurityException("verified actor and tenant identifiers must be UUIDs", invalidIdentity);
        }

        Duration timeout = Duration.ofMillis(timeoutMillis);
        try {
            Boolean activeMember = memberships.isMember(principal, tenant).await().atMost(timeout);
            if (!Boolean.TRUE.equals(activeMember)) {
                throw new SecurityException("active tenant membership is required");
            }

                String resourceId = holdId == null ? departureId.toString() : holdId.toString();
            requireAllowedForResource(caller, principalName, operation, resourceId);
            AccessDecision decision = authorization.authorize(request).await().atMost(timeout);
            if (decision == null || !decision.isAllowed()) {
                throw new SecurityException("booking permission was denied");
            }
        } catch (SecurityException denied) {
            throw denied;
        } catch (RuntimeException unavailable) {
            // Fail closed on timeouts, null results, and unavailable policy providers.
            throw new TransportAuthorizationUnavailableException("tenant membership or authorization service is unavailable", unavailable);
        }
    }

    public void requireAllowedForResource(TransportIdentityContext caller, String principalName,
                                          BookingOperation operation, String resourceId) {
        if (caller == null || principalName == null || principalName.isBlank()
                || resourceId == null || resourceId.isBlank()) {
            throw new SecurityException("verified caller identity and resource are required");
        }
        if (timeoutMillis < 100 || timeoutMillis > 10_000) {
            throw new TransportAuthorizationUnavailableException("authorization timeout configuration is invalid");
        }
        final Principal principal;
        final TenantId tenant;
        try {
            principal = new Principal(PrincipalId.fromString(caller.actorId()), principalName, Set.of());
            tenant = TenantId.of(caller.tenantId());
        } catch (RuntimeException invalidIdentity) {
            throw new SecurityException("verified actor and tenant identifiers must be UUIDs", invalidIdentity);
        }
        Duration timeout = Duration.ofMillis(timeoutMillis);
        try {
            Boolean activeMember = memberships.isMember(principal, tenant).await().atMost(timeout);
            if (!Boolean.TRUE.equals(activeMember)) throw new SecurityException("active tenant membership is required");
            AccessRequest request = new AccessRequest(principal, tenant, operation.actionType(),
                    operation.resourceType(), resourceId, "operation=" + operation.permissionAction());
            AccessDecision decision = authorization.authorize(request).await().atMost(timeout);
            if (decision == null || !decision.isAllowed()) throw new SecurityException("booking permission was denied");
        } catch (SecurityException denied) {
            throw denied;
        } catch (RuntimeException unavailable) {
            throw new TransportAuthorizationUnavailableException(
                    "tenant membership or authorization service is unavailable", unavailable);
        }
    }

    public enum BookingOperation {
        CREATE_HOLD("create", "transport.booking.hold", ActionType.CREATE),
        CONFIRM_HOLD("confirm", "transport.booking.hold", ActionType.UPDATE),
        RELEASE_HOLD("release", "transport.booking.hold", ActionType.UPDATE),
        CREATE_CHECKOUT("create", "transport.booking.checkout", ActionType.CREATE),
        READ_CHECKOUT("read", "transport.booking.checkout", ActionType.READ),
        REFRESH_CHECKOUT("refresh", "transport.booking.checkout", ActionType.PERFORM),
        REQUEST_REFUND("refund", "transport.booking.checkout", ActionType.PERFORM),
        READ_RECONCILIATION("read", "transport.booking.reconciliation", ActionType.READ);

        private final String permissionAction;
        private final String resourceType;
        private final ActionType actionType;

        BookingOperation(String permissionAction, String resourceType, ActionType actionType) {
            this.permissionAction = permissionAction;
            this.resourceType = resourceType;
            this.actionType = actionType;
        }

        public String permissionAction() { return permissionAction; }
        public String resourceType() { return resourceType; }
        public ActionType actionType() { return actionType; }
    }
}
