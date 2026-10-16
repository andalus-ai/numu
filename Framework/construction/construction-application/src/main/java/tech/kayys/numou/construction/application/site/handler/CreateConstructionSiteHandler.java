package tech.kayys.syirkah.construction.application.site.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.site.command.CreateConstructionSiteCommand;
import tech.kayys.syirkah.construction.domain.site.ConstructionSite;
import tech.kayys.syirkah.construction.spi.site.ConstructionSiteRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreateConstructionSiteHandler implements CommandHandler<CreateConstructionSiteCommand, ConstructionSite> {
    private final ConstructionSiteRepository repository;
    private final EventPublisher eventPublisher;

    public CreateConstructionSiteHandler(ConstructionSiteRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<ConstructionSite> handle(CreateConstructionSiteCommand command) {
        var site = ConstructionSite.create(
                command.projectId(),
                command.siteCode(),
                command.name(),
                command.type(),
                command.address(),
                command.latitude(),
                command.longitude(),
                command.timezone()
        );
        return Uni.createFrom()
                .completionStage(repository.save(site))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}
