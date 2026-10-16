package tech.kayys.syirkah.commerce.pricing.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.pricing.application.command.ActivatePriceListCommand;
import tech.kayys.syirkah.commerce.pricing.domain.price.PriceListId;
import tech.kayys.syirkah.commerce.pricing.spi.port.PriceListRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;

/** Lifecycle transition for a price list. */
public final class ActivatePriceListHandler
        implements CommandHandler<ActivatePriceListCommand, Result<PriceListId>> {

    private static final ApplicationError NOT_FOUND = ApplicationError.of(
            "PRICE_LIST_NOT_FOUND", "Price list does not exist");

    private final PriceListRepository lists;

    public ActivatePriceListHandler(PriceListRepository lists) {
        this.lists = Objects.requireNonNull(lists);
    }

    @Override
    public Uni<Result<PriceListId>> handle(ActivatePriceListCommand command) {
        return Uni.createFrom()
                .completionStage(lists.findById(command.priceListId()))
                .onItem().transformToUni(maybe -> {
                    if (maybe.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(NOT_FOUND));
                    }
                    var list = maybe.get();
                    try {
                        list.activate();
                    } catch (RuntimeException ex) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of(
                                "PRICE_LIST_RULE_VIOLATION", ex.getMessage())));
                    }
                    return Uni.createFrom()
                            .completionStage(lists.save(list))
                            .map(saved -> Result.success(saved.id()));
                });
    }
}
