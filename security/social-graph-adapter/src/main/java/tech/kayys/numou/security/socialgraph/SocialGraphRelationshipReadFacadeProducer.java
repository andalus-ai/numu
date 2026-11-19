package tech.kayys.numou.security.socialgraph;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef;
import tech.kayys.numou.ecosystem.domain.party.PartyId;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
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

/**
 * Optional read composition. A host must supply an authoritative,
 * fail-closed profile visibility/consent implementation before this boundary
 * can be used; Social Graph never treats a FOLLOW edge as profile permission.
 */
@ApplicationScoped
public class SocialGraphRelationshipReadFacadeProducer {
    private final SocialGraphActorResolver actors;
    private final SocialGraphRelationshipQueryPort queries;
    private final SocialGraphRelationshipPolicyPort policies;
    private final SocialGraphRelationshipVisibilityPort visibility;

    @Inject
    public SocialGraphRelationshipReadFacadeProducer(
            SocialGraphActorResolver actors,
            SocialGraphRelationshipQueryPort queries,
            SocialGraphRelationshipPolicyPort policies,
            SocialGraphRelationshipVisibilityPort visibility) {
        this.actors = Objects.requireNonNull(actors, "actors cannot be null");
        this.queries = Objects.requireNonNull(queries, "queries cannot be null");
        this.policies = Objects.requireNonNull(policies, "policies cannot be null");
        this.visibility = Objects.requireNonNull(visibility, "visibility cannot be null");
    }

    @Produces
    @ApplicationScoped
    public SocialGraphRelationshipReadInvoker socialGraphRelationshipReadInvoker() {
        Clock clock = Clock.systemUTC();
        var policyService = new SocialGraphPolicyService(policies, clock);
        var readService = new SocialGraphRelationshipReadService(queries, visibility, policyService, clock);
        SocialGraphRelationshipReadFacade facade = new SocialGraphRelationshipReadFacade(actors, readService);
        return new SocialGraphRelationshipReadInvoker() {
            @Override
            public Uni<SocialGraphRelationshipReadPage> following(
                    TenantId tenant, IdentitySubjectRef subject, String cursor, int limit) {
                return facade.following(tenant, subject, cursor, limit);
            }

            @Override
            public Uni<SocialGraphRelationshipReadPage> followers(
                    TenantId tenant, IdentitySubjectRef subject, PartyId profileOwner, String cursor, int limit) {
                return facade.followers(tenant, subject, profileOwner, cursor, limit);
            }
        };
    }
}
