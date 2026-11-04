package tech.kayys.numou.security.socialgraph;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import tech.kayys.numou.socialgraph.application.PartyDirectoryPort;
import tech.kayys.numou.socialgraph.application.SocialGraphActorResolver;
import tech.kayys.numou.socialgraph.application.SocialGraphAuthorizationPort;
import tech.kayys.numou.socialgraph.application.SocialGraphCommandFacade;
import tech.kayys.numou.socialgraph.application.SocialGraphService;
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
        this.unitOfWork = Objects.requireNonNull(unitOfWork);
        this.parties = Objects.requireNonNull(parties);
        this.authorization = Objects.requireNonNull(authorization);
        this.actors = Objects.requireNonNull(actors);
    }

    @Produces
    @ApplicationScoped
    public SocialGraphCommandInvoker socialGraphCommandInvoker() {
        SocialGraphService service = new SocialGraphService(unitOfWork, parties, authorization, Clock.systemUTC());
        SocialGraphCommandFacade facade = new SocialGraphCommandFacade(actors, service);
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
        };
    }
}
