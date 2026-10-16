package tech.kayys.syirkah.commerce.pricing.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.pricing.application.command.AddPriceEntryCommand;
import tech.kayys.syirkah.commerce.pricing.domain.price.PriceEntry;
import tech.kayys.syirkah.commerce.pricing.spi.port.PriceListRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;

/** Adds or changes one price-list row (upsert by offering). */
public final class AddPriceEntryHandler
        implements CommandHandler<AddPriceEntryCommand, Result<PriceEntry>> {

    private static final ApplicationError NOT_FOUND = ApplicationError.of(
            "PRICE_LIST_NOT_FOUND", "Price list does not exist");

    private final PriceListRepository lists;

    public AddPriceEntryHandler(PriceListRepository lists) {
        this.lists = Objects.requireNonNull(lists);
    }

    @Override
    public Uni<Result<PriceEntry>> handle(AddPriceEntryCommand command) {
        return Uni.createFrom()
                .completionStage(lists.findById(command.priceListId()))
                .onItem().transformToUni(maybe -> {
                    if (maybe.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(NOT_FOUND));
                    }
                    var list = maybe.get();
                    try {
                        if (list.entryFor(command.offeringId()).isPresent()) {
                            list.changeEntry(command.offeringId(), command.amount());
                        } else {
                            list.addEntry(command.offeringId(), command.amount());
                        }
                    } catch (RuntimeException ex) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of(
                                "PRICE_LIST_RULE_VIOLATION", ex.getMessage())));
                    }
                    return Uni.createFrom()
                            .completionStage(lists.save(list))
                            .map(saved -> Result.success(
                                    saved.entryFor(command.offeringId()).orElseThrow()));
                });
    }
}
