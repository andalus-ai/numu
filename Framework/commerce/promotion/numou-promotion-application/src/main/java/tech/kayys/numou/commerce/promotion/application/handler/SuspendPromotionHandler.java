package tech.kayys.syirkah.commerce.promotion.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.promotion.application.command.SuspendPromotionCommand;
import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.spi.port.CompiledPromotionProvider;
import tech.kayys.syirkah.commerce.promotion.spi.port.PromotionRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;
import java.util.Optional;

/** Deactivates a promotion and drops its compiled cache entry. */
public final class SuspendPromotionHandler
        implements CommandHandler<SuspendPromotionCommand, Result<PromotionId>> {

    private static final ApplicationError NOT_FOUND = ApplicationError.of(
            "PROMOTION_NOT_FOUND", "Promotion does not exist");

    private final PromotionRepository promotions;
    private final CompiledPromotionProvider compiledPromotions;

    public SuspendPromotionHandler(
            PromotionRepository promotions,
            CompiledPromotionProvider compiledPromotions
    ) {
        this.promotions = Objects.requireNonNull(promotions);
        this.compiledPromotions = Objects.requireNonNull(compiledPromotions);
    }

    @Override
    public Uni<Result<PromotionId>> handle(SuspendPromotionCommand command) {
        return Uni.createFrom()
                .completionStage(promotions.findById(command.promotionId()))
                .onItem()
                .transformToUni(this::suspend);
    }

    private Uni<Result<PromotionId>> suspend(Optional<Promotion> maybe) {
        if (maybe.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var suspended = maybe.get().deactivate();
        return Uni.createFrom()
                .completionStage(promotions.save(suspended))
                .onItem()
                .transformToUni(saved -> Uni.createFrom()
                        .completionStage(compiledPromotions.invalidate(saved.id()))
                        .replaceWith(Result.success(saved.id())));
    }
}
