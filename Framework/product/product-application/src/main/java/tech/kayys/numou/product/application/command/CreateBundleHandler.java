package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.bundle.Bundle;
import tech.kayys.syirkah.product.domain.bundle.BundleId;
import tech.kayys.syirkah.product.spi.port.BundleRepository;

import java.util.Objects;

/**
 * Creates a draft bundle, rejecting a duplicate bundle code.
 */
public final class CreateBundleHandler
        implements CommandHandler<CreateBundleCommand, Result<BundleId>> {

    private static final ApplicationError CODE_ALREADY_EXISTS =
            ApplicationError.of(
                    "BUNDLE_CODE_ALREADY_EXISTS",
                    "A bundle with this code already exists"
            );

    private final BundleRepository bundles;
    private final EventPublisher eventPublisher;

    public CreateBundleHandler(
            BundleRepository bundles,
            EventPublisher eventPublisher
    ) {
        this.bundles = Objects.requireNonNull(bundles);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<BundleId>> handle(CreateBundleCommand command) {
        return Uni.createFrom()
                .completionStage(bundles.existsByCode(command.code()))
                .onItem()
                .transformToUni(exists ->
                        exists ? duplicate() : create(command));
    }

    private Uni<Result<BundleId>> duplicate() {
        return Uni.createFrom().item(Result.failure(CODE_ALREADY_EXISTS));
    }

    private Uni<Result<BundleId>> create(CreateBundleCommand command) {
        var bundle = Bundle.create(
                BundleId.generate(),
                command.code(),
                command.name()
        );

        return Uni.createFrom()
                .completionStage(bundles.save(bundle))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
