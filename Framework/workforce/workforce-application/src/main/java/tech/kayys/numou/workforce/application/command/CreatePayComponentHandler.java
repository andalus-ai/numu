package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.paycomponent.PayComponent;
import tech.kayys.numou.workforce.domain.paycomponent.PayComponentId;
import tech.kayys.numou.workforce.spi.port.PayComponentRepository;

import java.util.Objects;

public class CreatePayComponentHandler implements CommandHandler<CreatePayComponentCommand, Result<PayComponentId>> {

    private final PayComponentRepository repository;
    private final EventPublisher eventPublisher;

    public CreatePayComponentHandler(PayComponentRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<PayComponentId>> handle(CreatePayComponentCommand cmd) {
        return Uni.createFrom().completionStage(repository.findByCode(cmd.tenantId(), cmd.code()))
                .chain(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("PAY_COMPONENT_EXISTS", "PayComponent with code " + cmd.code() + " already exists")));
                    }
                    PayComponent component;
                    try {
                        component = PayComponent.create(
                                PayComponentId.generate(),
                                cmd.tenantId(),
                                cmd.code(),
                                cmd.name(),
                                cmd.type()
                        );
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("INVALID_PAY_COMPONENT", e.getMessage())));
                    }
                    return Uni.createFrom().completionStage(repository.save(component))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}
