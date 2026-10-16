package tech.kayys.syirkah.commerce.promotion.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.promotion.application.command.CreatePromotionCommand;
import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.spi.port.PromotionRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;

/** Persists a new draft promotion. */
public final class CreatePromotionHandler
        implements CommandHandler<CreatePromotionCommand, Result<PromotionId>> {

    private static final ApplicationError NAME_EXISTS = ApplicationError.of(
            "PROMOTION_NAME_ALREADY_EXISTS",
            "A promotion with this name already exists");

    private final PromotionRepository promotions;

    public CreatePromotionHandler(PromotionRepository promotions) {
        this.promotions = Objects.requireNonNull(promotions);
    }

    @Override
    public Uni<Result<PromotionId>> handle(CreatePromotionCommand command) {
        return Uni.createFrom()
                .completionStage(promotions.existsByName(command.name()))
                .onItem()
                .transformToUni(exists -> {
                    if (exists) {
                        return Uni.createFrom().item(Result.failure(NAME_EXISTS));
                    }
                    var promotion = Promotion.draft(
                            PromotionId.generate(),
                            command.name(),
                            command.priority(),
                            command.validFor(),
                            command.rule(),
                            command.stacking());
                    return Uni.createFrom()
                            .completionStage(promotions.save(promotion))
                            .map(saved -> Result.success(saved.id()));
                });
    }
}
