package tech.kayys.syirkah.commerce.offering.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.offering.application.command.CreateProductOfferingCommand;
import tech.kayys.syirkah.commerce.offering.domain.ProductOffering;
import tech.kayys.syirkah.commerce.offering.domain.ProductOfferingId;
import tech.kayys.syirkah.commerce.offering.spi.port.ProductOfferingRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;

public final class CreateProductOfferingHandler
        implements CommandHandler<CreateProductOfferingCommand, Result<ProductOfferingId>> {

    private final ProductOfferingRepository repository;
    private final EventPublisher eventPublisher;

    public CreateProductOfferingHandler(
            ProductOfferingRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<ProductOfferingId>> handle(CreateProductOfferingCommand command) {
        var id = ProductOfferingId.generate();
        var offering = ProductOffering.create(
                id,
                command.productId(),
                command.name(),
                command.type(),
                command.channelId(),
                command.sellerId(),
                command.validity()
        );

        return Uni.createFrom()
                .completionStage(repository.save(offering))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }
}
