package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.product.Product;
import tech.kayys.syirkah.product.domain.specification.ProductSpecification;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;
import tech.kayys.syirkah.product.spi.port.ProductRepository;
import tech.kayys.syirkah.product.spi.port.ProductSpecificationRepository;

import java.util.Objects;
import java.util.Optional;

/** Creates a specification for an existing product (one spec per product). */
public final class CreateSpecificationHandler
        implements CommandHandler<
        CreateSpecificationCommand, Result<ProductSpecificationId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "PRODUCT_NOT_FOUND",
                    "Product does not exist"
            );

    private static final ApplicationError ALREADY_EXISTS =
            ApplicationError.of(
                    "PRODUCT_SPECIFICATION_ALREADY_EXISTS",
                    "Product already has a specification"
            );

    private final ProductRepository products;
    private final ProductSpecificationRepository specifications;
    private final EventPublisher eventPublisher;

    public CreateSpecificationHandler(
            ProductRepository products,
            ProductSpecificationRepository specifications,
            EventPublisher eventPublisher
    ) {
        this.products = Objects.requireNonNull(products);
        this.specifications = Objects.requireNonNull(specifications);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductSpecificationId>> handle(
            CreateSpecificationCommand command
    ) {
        return Uni.createFrom()
                .completionStage(products.findById(command.productId()))
                .onItem()
                .transformToUni(maybeProduct ->
                        create(maybeProduct, command));
    }

    private Uni<Result<ProductSpecificationId>> create(
            Optional<Product> maybeProduct,
            CreateSpecificationCommand command
    ) {
        if (maybeProduct.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        return Uni.createFrom()
                .completionStage(
                        specifications.existsByProductId(command.productId()))
                .onItem()
                .transformToUni(exists -> {
                    if (exists) {
                        return Uni.createFrom().item(
                                Result.failure(ALREADY_EXISTS));
                    }
                    return persist(command);
                });
    }

    private Uni<Result<ProductSpecificationId>> persist(
            CreateSpecificationCommand command
    ) {
        var specification = ProductSpecification.create(
                ProductSpecificationId.generate(),
                command.productId(),
                command.code(),
                command.name()
        );

        return Uni.createFrom()
                .completionStage(specifications.save(specification))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
