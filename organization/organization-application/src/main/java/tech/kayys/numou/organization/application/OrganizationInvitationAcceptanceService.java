package tech.kayys.numou.organization.application;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.identity.domain.user.UserId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Accepts a bearer secret only for the authenticated, active Identity user's verified email. */
public final class OrganizationInvitationAcceptanceService {
    private final VerifiedOrganizationInvitationIdentityPort identities;
    private final OrganizationInvitationAcceptancePort acceptance;
    private final Clock clock;

    public OrganizationInvitationAcceptanceService(VerifiedOrganizationInvitationIdentityPort identities,
            OrganizationInvitationAcceptancePort acceptance, Clock clock) {
        this.identities = Objects.requireNonNull(identities);
        this.acceptance = Objects.requireNonNull(acceptance);
        this.clock = Objects.requireNonNull(clock);
    }

    public CompletionStage<OrganizationInvitationAcceptancePort.AcceptedInvitation> accept(
            TenantId tenantId, String oneTimeSecret, UserId authenticatedUserId) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(oneTimeSecret);
        Objects.requireNonNull(authenticatedUserId);
        if (oneTimeSecret.isBlank() || oneTimeSecret.length() > 256) {
            throw new IllegalArgumentException("Invalid invitation secret");
        }
        return identities.requireVerifiedIdentity(authenticatedUserId).thenCompose(identity -> {
            if (!authenticatedUserId.equals(identity.userId())) {
                throw new SecurityException("Identity resolver returned a different principal");
            }
            return acceptance.accept(tenantId, digest(oneTimeSecret), authenticatedUserId,
                    identity.verifiedEmail(), clock.instant());
        });
    }

    private static String digest(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}