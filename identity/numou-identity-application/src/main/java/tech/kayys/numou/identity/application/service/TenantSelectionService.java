package tech.kayys.numou.identity.application.service;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.application.dto.AccessTokenResponse;
import tech.kayys.numou.identity.application.port.MembershipManagementPort;
import tech.kayys.numou.identity.application.port.TenantDiscoveryPort;
import tech.kayys.numou.identity.application.port.TenantTokenIssuerPort;
import tech.kayys.numou.identity.domain.user.UserId;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Tenant discovery and exchange for a verified, authenticated human user. */
public class TenantSelectionService {
    private static final long ACCESS_TOKEN_LIFETIME_SECONDS = 900;

    private final TenantDiscoveryPort discovery;
    private final MembershipManagementPort memberships;
    private final TenantTokenIssuerPort tokenIssuer;

    public TenantSelectionService(TenantDiscoveryPort discovery, MembershipManagementPort memberships,
                                   TenantTokenIssuerPort tokenIssuer) {
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.tokenIssuer = Objects.requireNonNull(tokenIssuer, "tokenIssuer");
    }

    public Uni<List<UUID>> eligibleTenants(UUID principalId) {
        if (principalId == null) {
            return failure("AUTHENTICATION_REQUIRED", "Authenticated user is required");
        }
        return discovery.activeTenantIds(UserId.of(principalId));
    }

    public Uni<AccessTokenResponse> exchange(UUID principalId, String displayName, UUID requestedTenantId) {
        if (principalId == null) {
            return failure("AUTHENTICATION_REQUIRED", "Authenticated user is required");
        }
        if (requestedTenantId == null) {
            return failure("TENANT_SELECTION_REQUIRED", "A tenant must be selected");
        }
        if (displayName == null || displayName.isBlank()) {
            return failure("INVALID_PRINCIPAL", "Authenticated user profile is incomplete");
        }

        TenantId tenantId = TenantId.of(requestedTenantId);
        UserId userId = UserId.of(principalId);
        return memberships.isActive(tenantId, userId)
                .chain(active -> {
                    if (!Boolean.TRUE.equals(active)) {
                        return failure("TENANT_MEMBERSHIP_REQUIRED",
                                "Active membership in the selected tenant is required");
                    }
                    String token = tokenIssuer.issueTenantAccessToken(principalId, displayName, requestedTenantId);
                    return Uni.createFrom().item(AccessTokenResponse.bearer(token, ACCESS_TOKEN_LIFETIME_SECONDS));
                });
    }

    private static <T> Uni<T> failure(String code, String message) {
        return Uni.createFrom().failure(ApplicationError.of(code, message).toException());
    }
}
