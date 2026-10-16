package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.variant.ProductVariant;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;
import tech.kayys.syirkah.product.spi.port.ProductVariantRepository;

import java.util.Objects;
import java.util.Optional;

/** Activates a product variant. Illegal transitions are rejected by the aggregate. */
public final class ActivateVariantHandler
        implements CommandHandler<ActivateVariantCommand, Result<ProductVariantId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "VARIANT_NOT_FOUND",
                    "Product variant does not exist"
            );

    private final ProductVariantRepository variants;
    private final EventPublisher eventPublisher;

    public ActivateVariantHandler(
            ProductVariantRepository variants,
            EventPublisher eventPublisher
    ) {
        this.variants = Objects.requireNonNull(variants);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductVariantId>> handle(ActivateVariantCommand command) {
        return Uni.createFrom()
                .completionStage(variants.findById(command.variantId()))
                .onItem()
                .transformToUni(this::activate);
    }

    private Uni<Result<ProductVariantId>> activate(
            Optional<ProductVariant> maybeVariant
    ) {
        if (maybeVariant.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var variant = maybeVariant.get();
        variant.activate();

        return Uni.createFrom()
                .completionStage(variants.save(variant))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
