package tech.kayys.numou.socialgraph.application;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

import java.util.Objects;
import java.util.Optional;

/**
 * Authenticated application boundary for relationship-list reads.
 *
 * <p>Callers provide only the tenant and issuer/subject established by their
 * trusted security adapter. The viewer Party is always resolved from the
 * tenant-scoped Ecosystem identity association; it is never accepted as a
 * caller-controlled argument. Profile-owner visibility and block policy are
 * enforced by {@link SocialGraphRelationshipReadService}.</p>
 */
public final class SocialGraphRelationshipReadFacade {
    private final SocialGraphActorResolver actors;
    private final SocialGraphRelationshipReadService reads;

    public SocialGraphRelationshipReadFacade(
            SocialGraphActorResolver actors,
            SocialGraphRelationshipReadService reads) {
        this.actors = Objects.requireNonNull(actors, "actors cannot be null");
        this.reads = Objects.requireNonNull(reads, "reads cannot be null");
    }

    /** Reads the authenticated Party's own following list. */
    public Uni<SocialGraphRelationshipReadPage> following(
            TenantId trustedTenant, IdentitySubjectRef authenticatedSubject, String cursor, int limit) {
        return withViewer(trustedTenant, authenticatedSubject,
                viewer -> reads.following(viewer.tenantId(), viewer.partyId(), cursor, limit));
    }

    /**
     * Reads a profile owner's followers. The profile owner is only a target;
     * the viewer is still derived from the verified identity association.
     */
    public Uni<SocialGraphRelationshipReadPage> followers(
            TenantId trustedTenant, IdentitySubjectRef authenticatedSubject,
            PartyId profileOwner, String cursor, int limit) {
        Objects.requireNonNull(profileOwner, "profileOwner cannot be null");
        return withViewer(trustedTenant, authenticatedSubject,
                viewer -> reads.followers(viewer.tenantId(), viewer.partyId(), profileOwner, cursor, limit));
    }

    private Uni<SocialGraphRelationshipReadPage> withViewer(
            TenantId trustedTenant,
            IdentitySubjectRef authenticatedSubject,
            java.util.function.Function<SocialGraphActor, Uni<SocialGraphRelationshipReadPage>> read) {
        Objects.requireNonNull(trustedTenant, "trustedTenant cannot be null");
        Objects.requireNonNull(authenticatedSubject, "authenticatedSubject cannot be null");
        Objects.requireNonNull(read, "read cannot be null");

        return actors.resolve(trustedTenant, authenticatedSubject)
                .onItem().transformToUni(resolved -> {
                    if (resolved == null || resolved.isEmpty()) {
                        return denied();
                    }
                    SocialGraphActor viewer = resolved.get();
                    if (!trustedTenant.equals(viewer.tenantId())) {
                        return denied();
                    }
                    return read.apply(viewer);
                });
    }

    private static Uni<SocialGraphRelationshipReadPage> denied() {
        return Uni.createFrom().failure(new SocialGraphAccessDeniedException());
    }
}
