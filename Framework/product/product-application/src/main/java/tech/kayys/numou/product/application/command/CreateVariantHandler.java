package tech.kayys.numou.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.product.domain.product.Product;
import tech.kayys.numou.product.domain.variant.ProductVariant;
import tech.kayys.numou.product.domain.variant.ProductVariantId;
import tech.kayys.numou.product.spi.port.ProductRepository;
import tech.kayys.numou.product.spi.port.ProductVariantRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Creates a variant for an existing product. The product must
 * exist - the variant references it by ID only.
 */
public final class CreateVariantHandler
        implements CommandHandler<
        CreateVariantCommand, Result<ProductVariantId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "PRODUCT_NOT_FOUND",
                    "Product does not exist"
            );

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final EventPublisher eventPublisher;

    public CreateVariantHandler(
            ProductRepository products,
            ProductVariantRepository variants,
            EventPublisher eventPublisher
    ) {
        this.products = Objects.requireNonNull(products);
        this.variants = Objects.requireNonNull(variants);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductVariantId>> handle(
            CreateVariantCommand command
    ) {
        return Uni.createFrom()
                .completionStage(products.findById(command.productId()))
                .onItem()
                .transformToUni(maybeProduct ->
                        create(maybeProduct, command));
    }

    private Uni<Result<ProductVariantId>> create(
            Optional<Product> maybeProduct,
            CreateVariantCommand command
    ) {
        if (maybeProduct.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var variant = ProductVariant.create(
                ProductVariantId.generate(),
                maybeProduct.get().id(),
                command.code(),
                command.name()
        );

        return Uni.createFrom()
                .completionStage(variants.save(variant))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}