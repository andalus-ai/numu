package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.product.Product;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.spi.port.ProductRepository;

import java.util.Objects;
import java.util.Optional;

/** Renames a product and/or changes its description. */
public final class UpdateProductHandler
        implements CommandHandler<UpdateProductCommand, Result<ProductId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "PRODUCT_NOT_FOUND",
                    "Product does not exist"
            );

    private final ProductRepository products;
    private final EventPublisher eventPublisher;

    public UpdateProductHandler(
            ProductRepository products,
            EventPublisher eventPublisher
    ) {
        this.products = Objects.requireNonNull(products);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductId>> handle(UpdateProductCommand command) {
        return Uni.createFrom()
                .completionStage(products.findById(command.productId()))
                .onItem()
                .transformToUni(maybeProduct ->
                        update(maybeProduct, command));
    }

    private Uni<Result<ProductId>> update(
            Optional<Product> maybeProduct,
            UpdateProductCommand command
    ) {
        if (maybeProduct.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var product = maybeProduct.get();
        product.rename(command.name());
        product.changeDescription(command.description());

        return Uni.createFrom()
                .completionStage(products.save(product))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}