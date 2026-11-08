package tech.kayys.numou.security.socialgraph;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.numou.socialgraph.application.PartyDirectoryPort;
import tech.kayys.numou.socialgraph.application.SocialGraphActorResolver;
import tech.kayys.numou.socialgraph.application.SocialGraphAuthorizationPort;
import tech.kayys.numou.socialgraph.application.SocialGraphCommandFacade;
import tech.kayys.numou.socialgraph.spi.SocialGraphUnitOfWork;

import java.time.Clock;
import java.util.Objects;

/** CDI composition of the service, PostgreSQL unit of work and Ecosystem adapters. */
@ApplicationScoped
public class SocialGraphCommandFacadeProducer {
    private final SocialGraphUnitOfWork unitOfWork;
    private final PartyDirectoryPort parties;
    private final SocialGraphAuthorizationPort authorization;
    private final SocialGraphActorResolver actors;

    @Inject
    public SocialGraphCommandFacadeProducer(SocialGraphUnitOfWork unitOfWork, PartyDirectoryPort parties,
            SocialGraphAuthorizationPort authorization, SocialGraphActorResolver actors) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
        this.parties = Objects.requireNonNull(parties, "parties cannot be null");
        this.authorization = Objects.requireNonNull(authorization, "authorization cannot be null");
        this.actors = Objects.requireNonNull(actors, "actors cannot be null");
    }

    @Produces
    @ApplicationScoped
    public SocialGraphCommandInvoker socialGraphCommandInvoker() {
        SocialGraphCommandFacade facade = SocialGraphCommandFacade.compose(
                unitOfWork, parties, authorization, actors, Clock.systemUTC());
        return new SocialGraphCommandInvoker() {
            @Override public io.smallrye.mutiny.Uni<tech.kayys.numou.socialgraph.domain.SocialConnection>
            requestConnection(tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                    tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef subject,
                    tech.kayys.numou.ecosystem.domain.party.PartyId recipient, String key) {
                return facade.requestConnection(tenant, subject, recipient, key);
            }
            @Override public io.smallrye.mutiny.Uni<tech.kayys.numou.socialgraph.domain.SocialConnection>
            acceptConnection(tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                    tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef subject,
                    tech.kayys.numou.socialgraph.domain.SocialConnectionId id, String key) {
                return facade.acceptConnection(tenant, subject, id, key);
            }
            @Override public io.smallrye.mutiny.Uni<tech.kayys.numou.socialgraph.domain.SocialConnection>
            rejectConnection(tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                    tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef subject,
                    tech.kayys.numou.socialgraph.domain.SocialConnectionId id, String key) {
                return facade.rejectConnection(tenant, subject, id, key);
            }
            @Override public io.smallrye.mutiny.Uni<tech.kayys.numou.socialgraph.domain.SocialConnection>
            cancelConnection(tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                    tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef subject,
                    tech.kayys.numou.socialgraph.domain.SocialConnectionId id, String key) {
                return facade.cancelConnection(tenant, subject, id, key);
            }
            @Override public io.smallrye.mutiny.Uni<tech.kayys.numou.socialgraph.domain.SocialConnection>
            removeConnection(tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                    tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef subject,
                    tech.kayys.numou.socialgraph.domain.SocialConnectionId id, String key) {
                return facade.removeConnection(tenant, subject, id, key);
            }
            @Override public io.smallrye.mutiny.Uni<tech.kayys.numou.socialgraph.domain.SocialRelationship>
            follow(tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                    tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef subject,
                    tech.kayys.numou.ecosystem.domain.party.PartyId target, String key) {
                return facade.follow(tenant, subject, target, key);
            }
            @Override public io.smallrye.mutiny.Uni<tech.kayys.numou.socialgraph.domain.SocialRelationship>
            unfollow(tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                    tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef subject,
                    tech.kayys.numou.ecosystem.domain.party.PartyId target, String key) {
                return facade.unfollow(tenant, subject, target, key);
            }
            @Override public io.smallrye.mutiny.Uni<tech.kayys.numou.socialgraph.domain.SocialRelationship>
            block(tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                    tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef subject,
                    tech.kayys.numou.ecosystem.domain.party.PartyId target, String key) {
                return facade.block(tenant, subject, target, key);
            }
            @Override public io.smallrye.mutiny.Uni<tech.kayys.numou.socialgraph.domain.SocialRelationship>
            unblock(tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                    tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef subject,
                    tech.kayys.numou.ecosystem.domain.party.PartyId target, String key) {
                return facade.unblock(tenant, subject, target, key);
            }
            @Override public io.smallrye.mutiny.Uni<tech.kayys.numou.socialgraph.domain.SocialRelationship>
            mute(tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                    tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef subject,
                    tech.kayys.numou.ecosystem.domain.party.PartyId target, String key) {
                return facade.mute(tenant, subject, target, key);
            }
            @Override public io.smallrye.mutiny.Uni<tech.kayys.numou.socialgraph.domain.SocialRelationship>
            unmute(tech.kayys.numou.foundation.domain.tenant.TenantId tenant,
                    tech.kayys.numou.ecosystem.domain.party.IdentitySubjectRef subject,
                    tech.kayys.numou.ecosystem.domain.party.PartyId target, String key) {
                return facade.unmute(tenant, subject, target, key);
            }
        };
    }
}
