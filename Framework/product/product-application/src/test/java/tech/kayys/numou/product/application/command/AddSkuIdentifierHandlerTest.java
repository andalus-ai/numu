package tech.kayys.syirkah.product.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.application.support.InMemorySkuRepository;
import tech.kayys.syirkah.product.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.product.domain.sku.Sku;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifier;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifierType;
import tech.kayys.syirkah.product.domain.sku.SkuId;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;
import tech.kayys.syirkah.product.domain.product.ProductId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@DisplayName("Add SKU identifier handler")
class AddSkuIdentifierHandlerTest {

    private final InMemorySkuRepository skus =
            new InMemorySkuRepository();

    private final RecordingEventPublisher events =
            new RecordingEventPublisher();

    private final AddSkuIdentifierHandler addIdentifier =
            new AddSkuIdentifierHandler(skus, events);

    private SkuId register(String code) {
        var sku = Sku.create(
                SkuId.generate(),
                ProductId.generate(),
                ProductVariantId.generate(),
                code,
                "Test SKU"
        );
        return skus.save(sku).toCompletableFuture().join().id();
    }

    private static String failureCode(Result<?> result) {
        return ((Result.Failure<?>) result).error().code();
    }

    @Test
    void addsSkuIdentifier() {
        var skuId = register("SKU-1");

        addIdentifier.handle(
                new AddSkuIdentifierCommand(
                        skuId,
                        new SkuIdentifier(
                                SkuIdentifierType.EAN,
                                "8991234567890"
                        )
                )
        ).await().indefinitely().orElseThrow();

        var stored = skus.findById(skuId).toCompletableFuture().join().orElseThrow();
        var identifier = stored.identifiers().stream()
                .filter(i -> i.type() == SkuIdentifierType.EAN)
                .findFirst().orElseThrow();

        assertEquals("8991234567890", identifier.value());
    }

    @Test
    void normalizesWhitespaceInBarcode() {
        var skuId = register("SKU-1");

        addIdentifier.handle(
                new AddSkuIdentifierCommand(
                        skuId,
                        new SkuIdentifier(
                                SkuIdentifierType.GTIN,
                                " 123456789012 "  // leading/trailing spaces
                        )
                )
        ).await().indefinitely().orElseThrow();

        var stored = skus.findById(skuId).toCompletableFuture().join().orElseThrow();
        var identifier = stored.identifiers().stream()
                .filter(i -> i.type() == SkuIdentifierType.GTIN)
                .findFirst().orElseThrow();

        assertEquals("123456789012", identifier.value());
    }

    @Test
    void stripsNonDigitCharactersForBarcodes() {
        var skuId = register("SKU-1");

        addIdentifier.handle(
                new AddSkuIdentifierCommand(
                        skuId,
                        new SkuIdentifier(
                                SkuIdentifierType.EAN,
                                "89-9123-4567-890"  // dashes
                        )
                )
        ).await().indefinitely().orElseThrow();

        var stored = skus.findById(skuId).toCompletableFuture().join().orElseThrow();
        var identifier = stored.identifiers().stream()
                .filter(i -> i.type() == SkuIdentifierType.EAN)
                .findFirst().orElseThrow();

        assertEquals("8991234567890", identifier.value());
    }

    @Test
    void rejectsInvalidUPCLength() {
        var skuId = register("SKU-1");

        var result = addIdentifier.handle(
                new AddSkuIdentifierCommand(
                        skuId,
                        new SkuIdentifier(
                                SkuIdentifierType.UPC,
                                "12345678901"  // only 11 digits
                        )
                )
        ).await().indefinitely();

        assertInstanceOf(Result.Failure.class, result);
        assertEquals("BUSINESS_RULE_VIOLATION", failureCode(result));
    }

    @Test
    void rejectsDuplicateAfterNormalization() {
        var skuId = register("SKU-1");

        addIdentifier.handle(
                new AddSkuIdentifierCommand(
                        skuId,
                        new SkuIdentifier(
                                SkuIdentifierType.EAN,
                                "8991234567890"
                        )
                )
        ).await().indefinitely().orElseThrow();

        var result = addIdentifier.handle(
                new AddSkuIdentifierCommand(
                        skuId,
                        new SkuIdentifier(
                                SkuIdentifierType.EAN,
                                " 8991234567890 "
                        )
                )
        ).await().indefinitely();

        assertInstanceOf(Result.Failure.class, result);
        assertEquals("BUSINESS_RULE_VIOLATION", failureCode(result));
    }

    @Test
    void returnsNotFoundWhenSkuDoesNotExist() {
        var result = addIdentifier.handle(
                new AddSkuIdentifierCommand(
                        SkuId.generate(),
                        new SkuIdentifier(
                                SkuIdentifierType.EAN,
                                "8991234567890"
                        )
                )
        ).await().indefinitely();

        assertInstanceOf(Result.Failure.class, result);
        assertEquals("SKU_NOT_FOUND", failureCode(result));
    }
}
