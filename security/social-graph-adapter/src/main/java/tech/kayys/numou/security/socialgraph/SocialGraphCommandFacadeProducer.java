package tech.kayys.numou.security.socialgraph;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import tech.kayys.numou.socialgraph.application.PartyDirectoryPort;
import tech.kayys.numou.socialgraph.application.SocialGraphActorResolver;
import tech.kayys.numou.socialgraph.application.SocialGraphAuthorizationPort;
import tech.kayys.numou.socialgraph.application.SocialGraphCommandFacade;
import tech.kayys.numou.socialgraph.application.SocialGraphService;
import tech.kayys.numou.socialgraph.spi.SocialGraphUnitOfWork;

import java.time.Clock;
import java.util.Objects;

/** CDI composition for the Social Graph application command boundary. */
@ApplicationScoped
public class SocialGraphCommandFacadeProducer {
    private final SocialGraphUnitOfWork unitOfWork;
    private final PartyDirectoryPort parties;
    private final SocialGraphAuthorizationPort authorization;
    private final SocialGraphActorResolver actors;

    public SocialGraphCommandFacadeProducer(
            SocialGraphUnitOfWork unitOfWork,
            PartyDirectoryPort parties,
            SocialGraphAuthorizationPort authorization,
            SocialGraphActorResolver actors) {
        this.unitOfWork = Objects.requireNonNull(unitOfWork, "unitOfWork cannot be null");
        this.parties = Objects.requireNonNull(parties, "parties cannot be null");
        this.authorization = Objects.requireNonNull(authorization, "authorization cannot be null");
        this.actors = Objects.requireNonNull(actors, "actors cannot be null");
    }

    @Produces
    @ApplicationScoped
    public SocialGraphCommandFacade socialGraphCommandFacade() {
        SocialGraphService service = new SocialGraphService(unitOfWork, parties, authorization, Clock.systemUTC());
        return new SocialGraphCommandFacade(actors, service);
    }
}
