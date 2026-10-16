package tech.kayys.syirkah.commerce.promotion.application.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.commerce.promotion.application.command.ActivatePromotionCommand;
import tech.kayys.syirkah.commerce.promotion.domain.Promotion;
import tech.kayys.syirkah.commerce.promotion.domain.PromotionId;
import tech.kayys.syirkah.commerce.promotion.domain.compiler.PromotionCompiler;
import tech.kayys.syirkah.commerce.promotion.domain.validation.PromotionValidator;
import tech.kayys.syirkah.commerce.promotion.spi.port.CompiledPromotionProvider;
import tech.kayys.syirkah.commerce.promotion.spi.port.PromotionRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Activation pipeline: load → compile → activate → cache compiled form
 * (product03.md).
 */
public final class ActivatePromotionHandler
        implements CommandHandler<ActivatePromotionCommand, Result<PromotionId>> {

    private static final ApplicationError NOT_FOUND = ApplicationError.of(
            "PROMOTION_NOT_FOUND", "Promotion does not exist");

    private final PromotionRepository promotions;
    private final CompiledPromotionProvider compiledPromotions;
    private final PromotionCompiler compiler;

    public ActivatePromotionHandler(
            PromotionRepository promotions,
            CompiledPromotionProvider compiledPromotions
    ) {
        this(promotions, compiledPromotions, new PromotionCompiler());
    }

    public ActivatePromotionHandler(
            PromotionRepository promotions,
            CompiledPromotionProvider compiledPromotions,
            PromotionCompiler compiler
    ) {
        this.promotions = Objects.requireNonNull(promotions);
        this.compiledPromotions = Objects.requireNonNull(compiledPromotions);
        this.compiler = Objects.requireNonNull(compiler);
    }

    @Override
    public Uni<Result<PromotionId>> handle(ActivatePromotionCommand command) {
        return Uni.createFrom()
                .completionStage(promotions.findById(command.promotionId()))
                .onItem()
                .transformToUni(this::activate);
    }

    private Uni<Result<PromotionId>> activate(Optional<Promotion> maybe) {
        if (maybe.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var draft = maybe.get();
        var validation = new PromotionValidator(compiler).validate(draft);
        if (!validation.valid()) {
            String message = validation.errors().stream()
                    .map(error -> error.code() + ": " + error.message())
                    .collect(Collectors.joining("; "));
            return Uni.createFrom().item(Result.failure(
                    ApplicationError.of("PROMOTION_VALIDATION_FAILED", message)));
        }
        var compilation = compiler.compile(draft);

        var activated = draft.activate();
        return Uni.createFrom()
                .completionStage(promotions.save(activated))
                .onItem()
                .transformToUni(saved -> Uni.createFrom()
                        .completionStage(compiledPromotions.put(
                                compilation.compiled().orElseThrow()))
                        .replaceWith(Result.success(saved.id())));
    }
}
