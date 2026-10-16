package tech.kayys.syirkah.commerce.configuration.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.configuration.application.command.CompleteProductConfigurationCommand;
import tech.kayys.syirkah.commerce.configuration.domain.ConfigurationValidator;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.syirkah.commerce.configuration.spi.port.ProductConfigurationRepository;
import tech.kayys.syirkah.commerce.configuration.spi.port.SpecificationLookupPort;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Validate against specification, then complete DRAFT → COMPLETED (product02.md).
 */
public final class CompleteProductConfigurationHandler
        implements CommandHandler<CompleteProductConfigurationCommand, Result<ProductConfigurationId>> {

    private static final ApplicationError NOT_FOUND = ApplicationError.of(
            "CONFIGURATION_NOT_FOUND", "Configuration does not exist");
    private static final ApplicationError NO_SPEC = ApplicationError.of(
            "SPECIFICATION_NOT_FOUND", "No specification exists for product");

    private final ProductConfigurationRepository configurations;
    private final SpecificationLookupPort specifications;
    private final EventPublisher events;

    public CompleteProductConfigurationHandler(
            ProductConfigurationRepository configurations,
            SpecificationLookupPort specifications,
            EventPublisher events
    ) {
        this.configurations = Objects.requireNonNull(configurations);
        this.specifications = Objects.requireNonNull(specifications);
        this.events = Objects.requireNonNull(events);
    }

    @Override
    public Uni<Result<ProductConfigurationId>> handle(
            CompleteProductConfigurationCommand command
    ) {
        return Uni.createFrom()
                .completionStage(configurations.findById(command.configurationId()))
                .onItem().transformToUni(this::complete);
    }

    private Uni<Result<ProductConfigurationId>> complete(
            Optional<ProductConfiguration> maybe
    ) {
        if (maybe.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }
        var configuration = maybe.get();
        return Uni.createFrom()
                .completionStage(specifications.findSpecificationsByProduct(
                        configuration.productId()))
                .onItem().transformToUni(specs -> {
                    if (specs.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(NO_SPEC));
                    }
                    var target = specs.stream()
                            .filter(s -> configuration.specificationId() == null
                                    || s.id().equals(configuration.specificationId()))
                            .findFirst()
                            .orElse(specs.getFirst());
                    var validation = ConfigurationValidator.validate(target, configuration);
                    if (!validation.isValid()) {
                        String message = validation.errors().stream()
                                .map(e -> e.code() + ": " + e.message())
                                .collect(Collectors.joining("; "));
                        return Uni.createFrom().item(Result.failure(
                                ApplicationError.of("CONFIGURATION_INVALID", message)));
                    }
                    configuration.complete();
                    return Uni.createFrom()
                            .completionStage(configurations.save(configuration))
                            .onItem().transformToUni(saved -> events
                                    .publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.id())));
                });
    }
}
