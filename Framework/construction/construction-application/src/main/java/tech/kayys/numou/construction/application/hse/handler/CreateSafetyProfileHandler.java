package tech.kayys.syirkah.construction.application.hse.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.hse.command.CreateSafetyProfileCommand;
import tech.kayys.syirkah.construction.domain.hse.SafetyProfile;
import tech.kayys.syirkah.construction.spi.hse.SafetyProfileRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class CreateSafetyProfileHandler implements CommandHandler<CreateSafetyProfileCommand, SafetyProfile> {
    private final SafetyProfileRepository repository;
    private final EventPublisher eventPublisher;

    public CreateSafetyProfileHandler(SafetyProfileRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<SafetyProfile> handle(CreateSafetyProfileCommand command) {
        var profile = SafetyProfile.create(command.projectId(), command.siteId(), command.name());
        return Uni.createFrom()
                .completionStage(repository.save(profile))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}
