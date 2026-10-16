package tech.kayys.syirkah.product.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import tech.kayys.syirkah.foundation.application.result.ApplicationError;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.product.domain.identifier.DefaultIdentifierNormalizer;
import tech.kayys.syirkah.product.domain.identifier.DefaultIdentifierValidator;
import tech.kayys.syirkah.product.domain.identifier.IdentifierNormalizer;
import tech.kayys.syirkah.product.domain.identifier.IdentifierValidator;
import tech.kayys.syirkah.product.domain.sku.Sku;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifier;
import tech.kayys.syirkah.product.domain.sku.SkuId;
import tech.kayys.syirkah.product.spi.port.SkuRepository;

import java.util.Objects;
import java.util.Optional;

/**
 * Adds an external identifier (barcode, EAN, ...) to a SKU.
 * Duplicates are rejected by the aggregate.
 * <p>
 * Normalizes the value first (e.g. " 123456789012 " → "123456789012")
 * and validates the shape, so uniqueness checks and persistence use
 * canonical values (product02.md).
 */
public final class AddSkuIdentifierHandler
        implements CommandHandler<AddSkuIdentifierCommand, Result<SkuId>> {

    private static final ApplicationError NOT_FOUND =
            ApplicationError.of("SKU_NOT_FOUND", "SKU does not exist");

    private static final ApplicationError BUSINESS_RULE =
            ApplicationError.of(
                    "BUSINESS_RULE_VIOLATION",
                    "The identifier is invalid or already exists"
            );

    private final SkuRepository skus;
    private final EventPublisher eventPublisher;
    private final IdentifierNormalizer normalizer;
    private final IdentifierValidator validator;

    public AddSkuIdentifierHandler(SkuRepository skus, EventPublisher eventPublisher) {
        this(skus, eventPublisher,
                new DefaultIdentifierNormalizer(),
                new DefaultIdentifierValidator());
    }

    public AddSkuIdentifierHandler(SkuRepository skus, EventPublisher eventPublisher,
                                   IdentifierNormalizer normalizer, IdentifierValidator validator) {
        this.skus = Objects.requireNonNull(skus);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.normalizer = Objects.requireNonNull(normalizer);
        this.validator = Objects.requireNonNull(validator);
    }

    @Override
    public Uni<Result<SkuId>> handle(AddSkuIdentifierCommand command) {
        return Uni.createFrom()
                .completionStage(skus.findById(command.skuId()))
                .onItem()
                .transformToUni(maybeSku -> add(maybeSku, command));
    }

    private Uni<Result<SkuId>> add(
            Optional<Sku> maybeSku,
            AddSkuIdentifierCommand command
    ) {
        if (maybeSku.isEmpty()) {
            return Uni.createFrom().item(Result.failure(NOT_FOUND));
        }

        var normalizedOr = normalize(command.identifier());
        if (normalizedOr.isFailure()) {
            return Uni.createFrom().item(Result.failure(BUSINESS_RULE));
        }

        var sku = maybeSku.get();
        var normalized = normalizedOr.orElseThrow();
        try {
            sku.addIdentifier(normalized);
        } catch (BusinessRuleViolation e) {
            return Uni.createFrom().item(Result.failure(BUSINESS_RULE));
        }

        return Uni.createFrom()
                .completionStage(skus.save(sku))
                .onItem()
                .transformToUni(saved -> eventPublisher
                        .publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(saved.id())));
    }

    private Result<SkuIdentifier> normalize(SkuIdentifier identifier) {
        var value = normalizer.normalize(
                identifier.type(), identifier.value());
        try {
            validator.validate(new SkuIdentifier(identifier.type(), value));
        } catch (BusinessRuleViolation | IllegalArgumentException e) {
            return Result.failure(BUSINESS_RULE);
        }
        return Result.success(new SkuIdentifier(identifier.type(), value));
    }
}