package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.classification.ClassificationScheme;
import tech.kayys.syirkah.product.domain.classification.ClassificationSchemeId;
import tech.kayys.syirkah.product.spi.port.ClassificationSchemeRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Archives an active classification scheme. Illegal transitions are
 * rejected by the aggregate itself.
 */
public final class ArchiveClassificationSchemeHandler
        implements CommandHandler<
        ArchiveClassificationSchemeCommand, Result<ClassificationSchemeId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "CLASSIFICATION_SCHEME_NOT_FOUND",
                    "Classification scheme does not exist"
            );

    private final ClassificationSchemeRepository schemes;
    private final EventPublisher eventPublisher;

    public ArchiveClassificationSchemeHandler(
            ClassificationSchemeRepository schemes,
            EventPublisher eventPublisher
    ) {
        this.schemes = Objects.requireNonNull(schemes);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ClassificationSchemeId>> handle(
            ArchiveClassificationSchemeCommand command
    ) {
        return Uni.createFrom()
                .completionStage(schemes.findById(command.schemeId()))
                .onItem()
                .transformToUni(this::archive);
    }

    private Uni<Result<ClassificationSchemeId>> archive(
            Optional<ClassificationScheme> maybeScheme
    ) {
        if (maybeScheme.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var scheme = maybeScheme.get();
        scheme.archive();

        return Uni.createFrom()
                .completionStage(schemes.save(scheme))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
