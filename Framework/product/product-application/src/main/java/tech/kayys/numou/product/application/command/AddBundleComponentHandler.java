package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.bundle.Bundle;
import tech.kayys.syirkah.product.domain.bundle.BundleId;
import tech.kayys.syirkah.product.spi.port.BundleRepository;
import tech.kayys.syirkah.product.spi.port.ProductRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Adds a component to a draft bundle after verifying the referenced
 * product exists.
 */
public final class AddBundleComponentHandler
        implements CommandHandler<
        AddBundleComponentCommand, Result<BundleId>> {

    private static final ApplicationError BUNDLE_NOT_FOUND =
            ApplicationError.of(
                    "BUNDLE_NOT_FOUND",
                    "Bundle does not exist"
            );

    private static final ApplicationError PRODUCT_NOT_FOUND =
            ApplicationError.of(
                    "PRODUCT_NOT_FOUND",
                    "Product does not exist"
            );

    private final BundleRepository bundles;
    private final ProductRepository products;
    private final EventPublisher eventPublisher;

    public AddBundleComponentHandler(
            BundleRepository bundles,
            ProductRepository products,
            EventPublisher eventPublisher
    ) {
        this.bundles = Objects.requireNonNull(bundles);
        this.products = Objects.requireNonNull(products);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<BundleId>> handle(
            AddBundleComponentCommand command
    ) {
        return Uni.createFrom()
                .completionStage(products.findById(command.productId()))
                .onItem()
                .transformToUni(maybeProduct -> {
                    if (maybeProduct.isEmpty()) {
                        return Uni.createFrom().item(
                                Result.failure(PRODUCT_NOT_FOUND)
                        );
                    }
                    return loadAndAdd(command);
                });
    }

    private Uni<Result<BundleId>> loadAndAdd(
            AddBundleComponentCommand command
    ) {
        return Uni.createFrom()
                .completionStage(bundles.findById(command.bundleId()))
                .onItem()
                .transformToUni(maybeBundle ->
                        add(maybeBundle, command));
    }

    private Uni<Result<BundleId>> add(
            Optional<Bundle> maybeBundle,
            AddBundleComponentCommand command
    ) {
        if (maybeBundle.isEmpty()) {
            return Uni.createFrom().item(Result.failure(BUNDLE_NOT_FOUND));
        }

        var bundle = maybeBundle.get();
        bundle.addComponent(command.productId(), command.quantity());

        return Uni.createFrom()
                .completionStage(bundles.save(bundle))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
