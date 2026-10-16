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

/** Adds an option to an existing option group on a specification. */
public final class AddSpecificationOptionHandler
        implements CommandHandler<
        AddSpecificationOptionCommand,
        Result<ProductSpecificationId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "SPECIFICATION_NOT_FOUND",
                    "Product specification does not exist"
            );

    private final ProductSpecificationRepository specifications;
    private final EventPublisher eventPublisher;

    public AddSpecificationOptionHandler(
            ProductSpecificationRepository specifications,
            EventPublisher eventPublisher
    ) {
        this.specifications = Objects.requireNonNull(specifications);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductSpecificationId>> handle(
            AddSpecificationOptionCommand command
    ) {
        return Uni.createFrom()
                .completionStage(specifications.findById(command.specificationId()))
                .onItem()
                .transformToUni(maybe -> add(maybe, command));
    }

    private Uni<Result<ProductSpecificationId>> add(
            Optional<ProductSpecification> maybe,
            AddSpecificationOptionCommand command
    ) {
        if (maybe.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var specification = maybe.get();
        specification.addOption(command.groupCode(), command.option());

        return Uni.createFrom()
                .completionStage(specifications.save(specification))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
