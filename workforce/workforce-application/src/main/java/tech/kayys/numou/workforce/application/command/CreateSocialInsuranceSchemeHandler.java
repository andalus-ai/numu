package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.socialinsurance.SocialInsuranceScheme;
import tech.kayys.numou.workforce.domain.socialinsurance.SocialInsuranceSchemeId;
import tech.kayys.numou.workforce.spi.port.SocialInsuranceSchemeRepository;

import java.util.Objects;

public class CreateSocialInsuranceSchemeHandler implements CommandHandler<CreateSocialInsuranceSchemeCommand, Result<SocialInsuranceSchemeId>> {

    private final SocialInsuranceSchemeRepository repository;
    private final EventPublisher eventPublisher;

    public CreateSocialInsuranceSchemeHandler(SocialInsuranceSchemeRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<SocialInsuranceSchemeId>> handle(CreateSocialInsuranceSchemeCommand cmd) {
        return Uni.createFrom().completionStage(repository.findByCode(cmd.tenantId(), cmd.code()))
                .chain(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("SCHEME_EXISTS", "Social insurance scheme with code " + cmd.code() + " already exists")));
                    }
                    SocialInsuranceScheme scheme;
                    try {
                        scheme = SocialInsuranceScheme.create(
                                SocialInsuranceSchemeId.generate(),
                                cmd.tenantId(),
                                cmd.code(),
                                cmd.name()
                        );
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("INVALID_SCHEME", e.getMessage())));
                    }
                    return Uni.createFrom().completionStage(repository.save(scheme))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}
