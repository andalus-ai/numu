package tech.kayys.numou.construction.application.site.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.construction.application.site.command.CreateConstructionSiteCommand;
import tech.kayys.numou.construction.domain.site.ConstructionSite;
import tech.kayys.numou.construction.spi.site.ConstructionSiteRepository;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
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
