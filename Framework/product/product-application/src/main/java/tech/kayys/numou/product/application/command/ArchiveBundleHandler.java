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
import java.util.Optional;

/** Archives a discontinued bundle — the terminal lifecycle state. */
public final class ArchiveBundleHandler
        implements CommandHandler<ArchiveBundleCommand, Result<BundleId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "BUNDLE_NOT_FOUND",
                    "Bundle does not exist"
            );

    private final BundleRepository bundles;
    private final EventPublisher eventPublisher;

    public ArchiveBundleHandler(
            BundleRepository bundles,
            EventPublisher eventPublisher
    ) {
        this.bundles = Objects.requireNonNull(bundles);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<BundleId>> handle(ArchiveBundleCommand command) {
        return Uni.createFrom()
                .completionStage(bundles.findById(command.bundleId()))
                .onItem()
                .transformToUni(this::archive);
    }

    private Uni<Result<BundleId>> archive(Optional<Bundle> maybeBundle) {
        if (maybeBundle.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var bundle = maybeBundle.get();
        bundle.archive();

        return Uni.createFrom()
                .completionStage(bundles.save(bundle))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
