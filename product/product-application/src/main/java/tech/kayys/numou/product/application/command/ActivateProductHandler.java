package tech.kayys.numou.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.product.domain.product.Product;
import tech.kayys.numou.product.domain.product.ProductId;
import tech.kayys.numou.product.spi.port.ProductRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Activates a draft product. Illegal transitions are rejected by
 * the aggregate itself.
 */
public final class ActivateProductHandler
        implements CommandHandler<ActivateProductCommand, Result<ProductId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "PRODUCT_NOT_FOUND",
                    "Product does not exist"
            );

    private final ProductRepository products;
    private final EventPublisher eventPublisher;

    public ActivateProductHandler(
            ProductRepository products,
            EventPublisher eventPublisher
    ) {
        this.products = Objects.requireNonNull(products);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductId>> handle(ActivateProductCommand command) {
        return Uni.createFrom()
                .completionStage(products.findById(command.productId()))
                .onItem()
                .transformToUni(this::activate);
    }

    private Uni<Result<ProductId>> activate(
            Optional<Product> maybeProduct
    ) {
        if (maybeProduct.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var product = maybeProduct.get();
        product.activate();

        return Uni.createFrom()
                .completionStage(products.save(product))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}