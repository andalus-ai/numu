package tech.kayys.numou.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.product.domain.specification.ProductSpecification;
import tech.kayys.numou.product.domain.specification.ProductSpecificationId;
import tech.kayys.numou.product.spi.port.ProductSpecificationRepository;

import java.util.Objects;
import java.util.Optional;

/** Removes an option group from a specification. */
public final class RemoveSpecificationOptionGroupHandler
        implements CommandHandler<
        RemoveSpecificationOptionGroupCommand,
        Result<ProductSpecificationId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of(
                    "SPECIFICATION_NOT_FOUND",
                    "Product specification does not exist"
            );

    private final ProductSpecificationRepository specifications;
    private final EventPublisher eventPublisher;

    public RemoveSpecificationOptionGroupHandler(
            ProductSpecificationRepository specifications,
            EventPublisher eventPublisher
    ) {
        this.specifications = Objects.requireNonNull(specifications);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductSpecificationId>> handle(
            RemoveSpecificationOptionGroupCommand command
    ) {
        return Uni.createFrom()
                .completionStage(specifications.findById(command.specificationId()))
                .onItem()
                .transformToUni(maybe -> remove(maybe, command));
    }

    private Uni<Result<ProductSpecificationId>> remove(
            Optional<ProductSpecification> maybe,
            RemoveSpecificationOptionGroupCommand command
    ) {
        if (maybe.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var specification = maybe.get();
        specification.removeOptionGroup(command.groupCode());

        return Uni.createFrom()
                .completionStage(specifications.save(specification))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
