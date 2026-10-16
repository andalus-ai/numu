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

/**
 * Creates a draft classification scheme, rejecting a duplicate code.
 */
public final class CreateClassificationSchemeHandler
        implements CommandHandler<
        CreateClassificationSchemeCommand, Result<ClassificationSchemeId>> {

    private static final ApplicationError CODE_ALREADY_EXISTS =
            ApplicationError.of(
                    "CLASSIFICATION_SCHEME_CODE_ALREADY_EXISTS",
                    "A classification scheme with this code already exists"
            );

    private final ClassificationSchemeRepository schemes;
    private final EventPublisher eventPublisher;

    public CreateClassificationSchemeHandler(
            ClassificationSchemeRepository schemes,
            EventPublisher eventPublisher
    ) {
        this.schemes = Objects.requireNonNull(schemes);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ClassificationSchemeId>> handle(
            CreateClassificationSchemeCommand command
    ) {
        return Uni.createFrom()
                .completionStage(schemes.existsByCode(command.code()))
                .onItem()
                .transformToUni(exists ->
                        exists ? duplicate() : create(command));
    }

    private Uni<Result<ClassificationSchemeId>> duplicate() {
        return Uni.createFrom().item(Result.failure(CODE_ALREADY_EXISTS));
    }

    private Uni<Result<ClassificationSchemeId>> create(
            CreateClassificationSchemeCommand command
    ) {
        var scheme = ClassificationScheme.create(
                ClassificationSchemeId.generate(),
                command.code(),
                command.name(),
                command.type()
        );

        return Uni.createFrom()
                .completionStage(schemes.save(scheme))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
