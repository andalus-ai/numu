package tech.kayys.syirkah.commerce.pricing.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.offering.domain.OfferingStatus;
import tech.kayys.syirkah.commerce.offering.spi.port.ProductOfferingRepository;
import tech.kayys.syirkah.commerce.pricing.application.query.ResolvePriceQuery;
import tech.kayys.syirkah.commerce.pricing.domain.PriceAdjustment;
import tech.kayys.syirkah.commerce.pricing.domain.PriceResult;
import tech.kayys.syirkah.commerce.pricing.spi.port.OptionPricePort;
import tech.kayys.syirkah.commerce.pricing.spi.port.PriceBookPort;
import tech.kayys.syirkah.foundation.application.query.QueryHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.ArrayList;
import java.util.Objects;

/**
 * Stateless price resolution: offering must exist and be ACTIVE,
 * base price comes from the price book, then one adjustment per
 * selected option (missing adjustment = +0, never a failure).
 */
public final class ResolvePriceHandler
        implements QueryHandler<ResolvePriceQuery, Result<PriceResult>> {

    private static final ApplicationError OFFERING_NOT_FOUND =
            ApplicationError.of("OFFERING_NOT_FOUND", "Offering does not exist");

    private static final ApplicationError OFFERING_NOT_ACTIVE =
            ApplicationError.of("OFFERING_NOT_ACTIVE", "Offering is not active");

    private static final ApplicationError PRICE_NOT_FOUND =
            ApplicationError.of("PRICE_NOT_FOUND", "No base price for offering");

    private final ProductOfferingRepository offerings;
    private final PriceBookPort priceBook;
    private final OptionPricePort optionPrices;

    public ResolvePriceHandler(
            ProductOfferingRepository offerings,
            PriceBookPort priceBook,
            OptionPricePort optionPrices
    ) {
        this.offerings = Objects.requireNonNull(offerings);
        this.priceBook = Objects.requireNonNull(priceBook);
        this.optionPrices = Objects.requireNonNull(optionPrices);
    }

    @Override
    public Uni<Result<PriceResult>> handle(ResolvePriceQuery query) {
        var context = query.context();
        return Uni.createFrom()
                .completionStage(offerings.findById(context.offeringId()))
                .onItem()
                .transformToUni(maybeOffering -> {
                    if (maybeOffering.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(OFFERING_NOT_FOUND));
                    }
                    if (maybeOffering.get().status() != OfferingStatus.ACTIVE) {
                        return Uni.createFrom().item(Result.failure(OFFERING_NOT_ACTIVE));
                    }
                    return Uni.createFrom()
                            .completionStage(priceBook.findBasePrice(context.offeringId()))
                            .onItem()
                            .transformToUni(maybeBase -> {
                                if (maybeBase.isEmpty()) {
                                    return Uni.createFrom()
                                            .item(Result.failure(PRICE_NOT_FOUND));
                                }
                                return resolveAdjustments(query, maybeBase.get());
                            });
                });
    }

    private Uni<Result<PriceResult>> resolveAdjustments(
            ResolvePriceQuery query,
            tech.kayys.syirkah.foundation.domain.valueobject.Money base
    ) {
        var context = query.context();
        Uni<Result<PriceResult>> acc = Uni.createFrom()
                .item(Result.success(PriceResult.flat(base)));
        for (var selection : context.configuration().selections()) {
            acc = acc.onItem().transformToUni(current -> {
                if (current.isFailure()) {
                    return Uni.createFrom().item(current);
                }
                return Uni.createFrom()
                        .completionStage(optionPrices.findAdjustment(
                                context.offeringId(),
                                selection.groupCode(),
                                selection.optionCode()))
                        .map(maybeDelta -> {
                            if (maybeDelta.isEmpty()) {
                                return current;
                            }
                            var delta = maybeDelta.get();
                            var previous = current.orElseThrow();
                            var adjustments = new ArrayList<>(previous.adjustments());
                            adjustments.add(new PriceAdjustment(
                                    delta,
                                    selection.groupCode() + ":" + selection.optionCode()));
                            return Result.success(PriceResult.of(base, adjustments));
                        });
            });
        }
        return acc;
    }
}
