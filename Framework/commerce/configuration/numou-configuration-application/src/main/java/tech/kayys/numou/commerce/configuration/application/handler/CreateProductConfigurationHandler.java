package tech.kayys.syirkah.commerce.configuration.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.configuration.application.command.CreateProductConfigurationCommand;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfiguration;
import tech.kayys.syirkah.commerce.configuration.domain.ProductConfigurationId;
import tech.kayys.syirkah.commerce.configuration.spi.port.ProductConfigurationRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.spi.port.ProductRepository;
import tech.kayys.syirkah.product.spi.port.ProductSpecificationRepository;

import java.util.Objects;

/**
 * Cross-aggregate create: product exists, specification belongs to product.
 */
public final class CreateProductConfigurationHandler
        implements CommandHandler<CreateProductConfigurationCommand, Result<ProductConfigurationId>> {

    private static final ApplicationError PRODUCT_NOT_FOUND = ApplicationError.of(
            "PRODUCT_NOT_FOUND", "Product does not exist");
    private static final ApplicationError SPEC_NOT_FOUND = ApplicationError.of(
            "SPECIFICATION_NOT_FOUND", "Product specification does not exist");
    private static final ApplicationError SPEC_MISMATCH = ApplicationError.of(
            "SPECIFICATION_PRODUCT_MISMATCH",
            "Product specification does not belong to product");

    private final ProductRepository products;
    private final ProductSpecificationRepository specifications;
    private final ProductConfigurationRepository configurations;
    private final EventPublisher events;

    public CreateProductConfigurationHandler(
            ProductRepository products,
            ProductSpecificationRepository specifications,
            ProductConfigurationRepository configurations,
            EventPublisher events
    ) {
        this.products = Objects.requireNonNull(products);
        this.specifications = Objects.requireNonNull(specifications);
        this.configurations = Objects.requireNonNull(configurations);
        this.events = Objects.requireNonNull(events);
    }

    @Override
    public Uni<Result<ProductConfigurationId>> handle(
            CreateProductConfigurationCommand command
    ) {
        return Uni.createFrom()
                .completionStage(products.findById(command.productId()))
                .onItem().transformToUni(maybeProduct -> {
                    if (maybeProduct.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(PRODUCT_NOT_FOUND));
                    }
                    return Uni.createFrom()
                            .completionStage(specifications.findById(command.specificationId()))
                            .onItem().transformToUni(maybeSpec -> {
                                if (maybeSpec.isEmpty()) {
                                    return Uni.createFrom().item(Result.failure(SPEC_NOT_FOUND));
                                }
                                var spec = maybeSpec.get();
                                if (!spec.productId().equals(command.productId())) {
                                    return Uni.createFrom().item(Result.failure(SPEC_MISMATCH));
                                }
                                var configuration = ProductConfiguration.create(
                                        command.configurationId(),
                                        command.productId(),
                                        command.specificationId());
                                return Uni.createFrom()
                                        .completionStage(configurations.save(configuration))
                                        .onItem().transformToUni(saved -> events
                                                .publish(saved.pullDomainEvents())
                                                .replaceWith(Result.success(saved.id())));
                            });
                });
    }
}
