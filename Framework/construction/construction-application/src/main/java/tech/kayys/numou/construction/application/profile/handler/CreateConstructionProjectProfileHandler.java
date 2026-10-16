package tech.kayys.syirkah.construction.application.profile.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.profile.command.CreateConstructionProjectProfileCommand;
import tech.kayys.syirkah.construction.domain.profile.ConstructionProjectProfile;
import tech.kayys.syirkah.construction.spi.profile.ConstructionProjectProfileRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreateConstructionProjectProfileHandler
        implements CommandHandler<CreateConstructionProjectProfileCommand, ConstructionProjectProfile> {

    private final ConstructionProjectProfileRepository repository;
    private final EventPublisher eventPublisher;

    public CreateConstructionProjectProfileHandler(
            ConstructionProjectProfileRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<ConstructionProjectProfile> handle(CreateConstructionProjectProfileCommand command) {
        return Uni.createFrom()
                .completionStage(repository.findByProjectId(command.projectId()))
                .onItem()
                .transformToUni(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().failure(new IllegalStateException("Construction profile already exists"));
                    }
                    var profile = ConstructionProjectProfile.create(
                            command.projectId(),
                            command.constructionType(),
                            command.deliveryMethod(),
                            command.contractType(),
                            command.description()
                    );
                    return Uni.createFrom()
                            .completionStage(repository.save(profile))
                            .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
                });
    }
}
