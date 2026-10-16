package tech.kayys.syirkah.commerce.pricing.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.pricing.application.command.CreatePriceListCommand;
import tech.kayys.syirkah.commerce.pricing.domain.price.PriceList;
import tech.kayys.syirkah.commerce.pricing.domain.price.PriceListId;
import tech.kayys.syirkah.commerce.pricing.spi.port.PriceListRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;

/** Persists a new draft price list. */
public final class CreatePriceListHandler
        implements CommandHandler<CreatePriceListCommand, Result<PriceListId>> {

    private static final ApplicationError CODE_EXISTS = ApplicationError.of(
            "PRICE_LIST_CODE_ALREADY_EXISTS", "A price list with this code already exists");

    private final PriceListRepository lists;

    public CreatePriceListHandler(PriceListRepository lists) {
        this.lists = Objects.requireNonNull(lists);
    }

    @Override
    public Uni<Result<PriceListId>> handle(CreatePriceListCommand command) {
        return Uni.createFrom()
                .completionStage(lists.existsByCode(command.code()))
                .onItem().transformToUni(exists -> {
                    if (exists) {
                        return Uni.createFrom().item(Result.failure(CODE_EXISTS));
                    }
                    var list = PriceList.create(
                            PriceListId.generate(), command.code(), command.name());
                    return Uni.createFrom()
                            .completionStage(lists.save(list))
                            .map(saved -> Result.success(saved.id()));
                });
    }
}
