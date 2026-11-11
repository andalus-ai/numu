package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipPolicyPort;

import java.time.Clock;
import java.util.Objects;

/**
 * Shared policy evaluator for Social Graph consumers.
 *
 * <p>Use this service before interactions and before delivering feed items.
 * It is deliberately not a public discovery API: profile visibility, consent,
 * moderation and tenant-discovery policy must also be evaluated by their
 * owning capabilities.</p>
 */
public final class SocialGraphPolicyService {
    private final SocialGraphRelationshipPolicyPort policies;
    private final Clock clock;

    public SocialGraphPolicyService(SocialGraphRelationshipPolicyPort policies, Clock clock) {
        this.policies = Objects.requireNonNull(policies, "policies cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    /** Returns true when either Party has an active, effective block against the other. */
    public Uni<Boolean> isBlocked(TenantId tenantId, PartyId first, PartyId second) {
        requireScope(tenantId, first, second);
        return policies.isBlocked(tenantId, first, second, clock.instant());
    }

    /** Interaction is permitted by the Social Graph block policy only; other authorization still applies. */
    public Uni<Boolean> canInteract(TenantId tenantId, PartyId actor, PartyId target) {
        return isBlocked(tenantId, actor, target).map(blocked -> !blocked);
    }

    /** Returns true only when viewer muted author; the reverse mute has no effect. */
    public Uni<Boolean> isMuted(TenantId tenantId, PartyId viewer, PartyId author) {
        requireScope(tenantId, viewer, author);
        return policies.isMuted(tenantId, viewer, author, clock.instant());
    }

    /** Suppresses delivery for a block in either direction or a viewer-owned mute. */
    public Uni<Boolean> shouldSuppressFromFeed(TenantId tenantId, PartyId viewer, PartyId author) {
        requireScope(tenantId, viewer, author);
        var now = clock.instant();
        return policies.isBlocked(tenantId, viewer, author, now)
                .flatMap(blocked -> blocked
                        ? Uni.createFrom().item(true)
                        : policies.isMuted(tenantId, viewer, author, now));
    }

    private static void requireScope(TenantId tenantId, PartyId first, PartyId second) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(first, "first Party cannot be null");
        Objects.requireNonNull(second, "second Party cannot be null");
    }
}
