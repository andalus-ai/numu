package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.domain.specification.ProductSpecification;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;
import tech.kayys.syirkah.product.spi.port.ProductSpecificationRepository;

import java.util.Objects;
import java.util.Optional;

/** Adds a typed attribute definition to a specification. */
public final class AddSpecificationAttributeHandler
        implements CommandHandler<
        AddSpecificationAttributeCommand,
        Result<ProductSpecificationId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "SPECIFICATION_NOT_FOUND",
                    "Product specification does not exist"
            );

    private final ProductSpecificationRepository specifications;
    private final EventPublisher eventPublisher;

    public AddSpecificationAttributeHandler(
            ProductSpecificationRepository specifications,
            EventPublisher eventPublisher
    ) {
        this.specifications = Objects.requireNonNull(specifications);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductSpecificationId>> handle(
            AddSpecificationAttributeCommand command
    ) {
        return Uni.createFrom()
                .completionStage(
                        specifications.findById(
                                command.specificationId()
                        )
                )
                .onItem()
                .transformToUni(maybeSpecification ->
                        add(maybeSpecification, command));
    }

    private Uni<Result<ProductSpecificationId>> add(
            Optional<ProductSpecification> maybeSpecification,
            AddSpecificationAttributeCommand command
    ) {
        if (maybeSpecification.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var specification = maybeSpecification.get();
        specification.addAttribute(command.attribute());

        return Uni.createFrom()
                .completionStage(specifications.save(specification))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}