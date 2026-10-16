package tech.kayys.syirkah.commerce.configuration.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.configuration.application.command.SelectOptionCommand;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.syirkah.commerce.configuration.domain.SelectedOption;
import tech.kayys.syirkah.commerce.configuration.spi.port.ProductConfigurationRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;
import java.util.Optional;

public final class SelectOptionHandler
        implements CommandHandler<SelectOptionCommand, Result<ProductConfigurationId>> {

    private static final ApplicationError NOT_FOUND = ApplicationError.of(
            "CONFIGURATION_NOT_FOUND", "Configuration does not exist");

    private final ProductConfigurationRepository configurations;
    private final EventPublisher events;

    public SelectOptionHandler(
            ProductConfigurationRepository configurations,
            EventPublisher events
    ) {
        this.configurations = Objects.requireNonNull(configurations);
        this.events = Objects.requireNonNull(events);
    }

    @Override
    public Uni<Result<ProductConfigurationId>> handle(SelectOptionCommand command) {
        return Uni.createFrom()
                .completionStage(configurations.findById(command.configurationId()))
                .onItem().transformToUni(maybe -> select(maybe, command));
    }

    private Uni<Result<ProductConfigurationId>> select(
            Optional<ProductConfiguration> maybe,
            SelectOptionCommand command
    ) {
        if (maybe.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }
        var configuration = maybe.get();
        configuration.selectOption(new SelectedOption(
                command.optionGroupId(), command.optionId()));
        return Uni.createFrom()
                .completionStage(configurations.save(configuration))
                .onItem().transformToUni(saved -> events
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
