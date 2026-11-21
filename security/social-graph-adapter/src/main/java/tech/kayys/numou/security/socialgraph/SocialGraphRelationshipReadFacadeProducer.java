package tech.kayys.numou.security.socialgraph;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.socialgraph.application.SocialGraphAccessDeniedException;
import tech.kayys.numou.socialgraph.application.SocialGraphActorResolver;
import tech.kayys.numou.socialgraph.application.SocialGraphPolicyService;
import tech.kayys.numou.socialgraph.application.SocialGraphRelationshipReadFacade;
import tech.kayys.numou.socialgraph.application.SocialGraphRelationshipReadPage;
import tech.kayys.numou.socialgraph.application.SocialGraphRelationshipReadService;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipPolicyPort;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipQueryPort;
import tech.kayys.numou.socialgraph.spi.SocialGraphRelationshipVisibilityPort;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Function;

/**
 * Composes authenticated relationship reads without inventing host profile
 * privacy policy. A host must provide exactly one visibility adapter. If it
 * does not, the read invoker fails closed at invocation time and command-only
 * Social Graph deployments remain bootable.
 */
@ApplicationScoped
public class SocialGraphRelationshipReadFacadeProducer {
    private final SocialGraphActorResolver actors;
    private final SocialGraphRelationshipQueryPort queries;
    private final SocialGraphRelationshipPolicyPort policies;
    private final Instance<SocialGraphRelationshipVisibilityPort> visibility;

    @Inject
    public SocialGraphRelationshipReadFacadeProducer(
            SocialGraphActorResolver actors,
            SocialGraphRelationshipQueryPort queries,
            SocialGraphRelationshipPolicyPort policies,
            Instance<SocialGraphRelationshipVisibilityPort> visibility) {
        this.actors = Objects.requireNonNull(actors, "actors cannot be null");
        this.queries = Objects.requireNonNull(queries, "queries cannot be null");
        this.policies = Objects.requireNonNull(policies, "policies cannot be null");
        this.visibility = Objects.requireNonNull(visibility, "visibility cannot be null");
    }

    @Produces
    @ApplicationScoped
    public SocialGraphRelationshipReadInvoker socialGraphRelationshipReadInvoker() {
        return new SocialGraphRelationshipReadInvoker() {
            @Override
            public Uni<SocialGraphRelationshipReadPage> following(
                    TenantId tenant, IdentitySubjectRef subject, String cursor, int limit) {
                return invoke(facade -> facade.following(tenant, subject, cursor, limit));
            }

            @Override
            public Uni<SocialGraphRelationshipReadPage> followers(
                    TenantId tenant, IdentitySubjectRef subject, PartyId profileOwner, String cursor, int limit) {
                return invoke(facade -> facade.followers(tenant, subject, profileOwner, cursor, limit));
            }
        };
    }

    private Uni<SocialGraphRelationshipReadPage> invoke(
            Function<SocialGraphRelationshipReadFacade, Uni<SocialGraphRelationshipReadPage>> operation) {
        if (!visibility.isResolvable()) return denied();
        try {
            SocialGraphRelationshipReadFacade facade = new SocialGraphRelationshipReadFacade(
                    actors,
                    new SocialGraphRelationshipReadService(
                            queries, visibility.get(), new SocialGraphPolicyService(policies, Clock.systemUTC()),
                            Clock.systemUTC()));
            return operation.apply(facade);
        } catch (SocialGraphAccessDeniedException denied) {
            return Uni.createFrom().failure(denied);
        }
    }

    private static Uni<SocialGraphRelationshipReadPage> denied() {
        return Uni.createFrom().failure(new SocialGraphAccessDeniedException());
    }
}
