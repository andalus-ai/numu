package tech.kayys.syirkah.commerce.configuration.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.configuration.application.command.DeselectOptionCommand;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.syirkah.commerce.configuration.spi.port.ProductConfigurationRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;
import java.util.Optional;

public final class DeselectOptionHandler
        implements CommandHandler<DeselectOptionCommand, Result<ProductConfigurationId>> {

    private static final ApplicationError NOT_FOUND = ApplicationError.of(
            "CONFIGURATION_NOT_FOUND", "Configuration does not exist");

    private final ProductConfigurationRepository configurations;
    private final EventPublisher events;

    public DeselectOptionHandler(
            ProductConfigurationRepository configurations,
            EventPublisher events
    ) {
        this.configurations = Objects.requireNonNull(configurations);
        this.events = Objects.requireNonNull(events);
    }

    @Override
    public Uni<Result<ProductConfigurationId>> handle(DeselectOptionCommand command) {
        return Uni.createFrom()
                .completionStage(configurations.findById(command.configurationId()))
                .onItem().transformToUni(maybe -> deselect(maybe, command));
    }

    private Uni<Result<ProductConfigurationId>> deselect(
            Optional<ProductConfiguration> maybe,
            DeselectOptionCommand command
    ) {
        if (maybe.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }
        var configuration = maybe.get();
        configuration.deselectOption(command.optionGroupId(), command.optionId());
        return Uni.createFrom()
                .completionStage(configurations.save(configuration))
                .onItem().transformToUni(saved -> events
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
