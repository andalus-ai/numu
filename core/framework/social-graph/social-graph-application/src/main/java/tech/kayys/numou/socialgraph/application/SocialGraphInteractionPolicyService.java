package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipPolicyPort;

import java.time.Clock;
import java.util.Objects;

/**
 * Shared policy gate for actions and delivery performed by other capabilities.
 *
 * <p>BLOCK is evaluated for every intent and is symmetric in the persistence
 * adapter. MUTE is evaluated only for feed and notification delivery and is
 * directional (recipient/viewer -> content author). This service does not
 * replace the consuming capability's own authorization, consent, visibility,
 * moderation, or eligibility checks.</p>
 */
public final class SocialGraphInteractionPolicyService {
    private final SocialGraphRelationshipPolicyPort policies;
    private final Clock clock;

    public SocialGraphInteractionPolicyService(SocialGraphRelationshipPolicyPort policies, Clock clock) {
        this.policies = Objects.requireNonNull(policies, "policies cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public Uni<SocialGraphInteractionDecision> evaluate(
            TenantId tenantId, PartyId actor, PartyId target, SocialGraphInteractionKind kind) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(actor, "actor cannot be null");
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        if (actor.equals(target)) {
            return Uni.createFrom().item(SocialGraphInteractionDecision.ALLOWED);
        }

        // One instant is used for both checks so an expiring relationship cannot
        // produce a mixed-time decision within a single evaluation.
        var evaluatedAt = clock.instant();
        return policies.isBlocked(tenantId, actor, target, evaluatedAt)
                .flatMap(blocked -> {
                    if (blocked) {
                        return Uni.createFrom().item(SocialGraphInteractionDecision.BLOCKED);
                    }
                    if (!kind.isDelivery()) {
                        return Uni.createFrom().item(SocialGraphInteractionDecision.ALLOWED);
                    }
                    return policies.isMuted(tenantId, actor, target, evaluatedAt)
                            .map(muted -> muted
                                    ? SocialGraphInteractionDecision.MUTED
                                    : SocialGraphInteractionDecision.ALLOWED);
                });
    }
}
