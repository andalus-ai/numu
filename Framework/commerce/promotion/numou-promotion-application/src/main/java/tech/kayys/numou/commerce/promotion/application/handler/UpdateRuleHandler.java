package tech.kayys.syirkah.commerce.promotion.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.promotion.application.command.UpdateRuleCommand;
import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionStatus;
import tech.kayys.syirkah.commerce.promotion.spi.port.PromotionRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;

public final class UpdateRuleHandler
        implements CommandHandler<UpdateRuleCommand, Result<PromotionId>> {

    private static final ApplicationError NOT_FOUND = ApplicationError.of(
            "PROMOTION_NOT_FOUND", "Promotion was not found");
    private static final ApplicationError NOT_DRAFT = ApplicationError.of(
            "PROMOTION_NOT_DRAFT", "Only draft promotions can update rules");

    private final PromotionRepository promotions;

    public UpdateRuleHandler(PromotionRepository promotions) {
        this.promotions = Objects.requireNonNull(promotions);
    }

    @Override
    public Uni<Result<PromotionId>> handle(UpdateRuleCommand command) {
        return Uni.createFrom()
                .completionStage(promotions.findById(command.promotionId()))
                .onItem()
                .transformToUni(opt -> {
                    if (opt.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(NOT_FOUND));
                    }
                    Promotion promotion = opt.get();
                    if (promotion.status() != PromotionStatus.DRAFT) {
                        return Uni.createFrom().item(Result.failure(NOT_DRAFT));
                    }
                    try {
                        Promotion updated = promotion.replaceRule(command.rule());
                        return Uni.createFrom()
                                .completionStage(promotions.save(updated))
                                .map(saved -> Result.success(saved.id()));
                    } catch (IllegalArgumentException ex) {
                        return Uni.createFrom().item(Result.failure(
                                ApplicationError.of("RULE_NOT_FOUND", ex.getMessage())));
                    }
                });
    }
}
