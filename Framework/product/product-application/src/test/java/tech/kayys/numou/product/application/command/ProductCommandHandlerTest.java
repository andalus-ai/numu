package tech.kayys.syirkah.product.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.product.application.support.InMemoryProductRepository;
import tech.kayys.syirkah.product.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.product.domain.event.ProductActivated;
import tech.kayys.syirkah.product.domain.event.ProductArchived;
import tech.kayys.syirkah.product.domain.event.ProductCreated;
import tech.kayys.syirkah.product.domain.event.ProductDiscontinued;
import tech.kayys.syirkah.product.domain.event.ProductIdentifierAdded;
import tech.kayys.syirkah.product.domain.event.ProductRenamed;
import tech.kayys.syirkah.product.domain.identifier.ProductIdentifierType;
import tech.kayys.syirkah.product.domain.identifier.ProductIdentifier;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.product.ProductStatus;
import tech.kayys.syirkah.product.domain.product.ProductType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Product command handlers")
class ProductCommandHandlerTest {

    private final InMemoryProductRepository products =
            new InMemoryProductRepository();

    private final RecordingEventPublisher events =
            new RecordingEventPublisher();

    private final CreateProductHandler createProduct =
            new CreateProductHandler(products, events);

    private final UpdateProductHandler updateProduct =
            new UpdateProductHandler(products, events);

    private final ActivateProductHandler activateProduct =
            new ActivateProductHandler(products, events);

    private final DiscontinueProductHandler discontinueProduct =
            new DiscontinueProductHandler(products, events);

    private final ArchiveProductHandler archiveProduct =
            new ArchiveProductHandler(products, events);

    private final AddProductIdentifierHandler addIdentifier =
            new AddProductIdentifierHandler(products, events);

    private ProductId register(String code) {
        return createProduct.handle(
                new CreateProductCommand(
                        code,
                        "Latte",
                        "Fresh milk coffee",
                        ProductType.PHYSICAL
                )
        ).await().indefinitely().orElseThrow();
    }

    private static String failureCode(Result<?> result) {
        return ((Result.Failure<?>) result).error().code();
    }

    @Test
    void createsProductAndPublishesEvent() {
        ProductId productId = register("COFFEE-LATTE");

        var created = assertInstanceOf(
                ProductCreated.class,
                events.published().getFirst()
        );

        assertEquals(productId, created.productId());
    }

    @Test
    void rejectsDuplicateProductCode() {
        register("COFFEE-LATTE");

        var result = createProduct.handle(
                new CreateProductCommand(
                        "COFFEE-LATTE",
                        "Another latte",
                        null,
                        ProductType.PHYSICAL
                )
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals(
                "PRODUCT_CODE_ALREADY_EXISTS",
                failureCode(result)
        );
    }

    @Test
    void reportsMissingProductOnActivation() {
        var result = activateProduct.handle(
                new ActivateProductCommand(ProductId.generate())
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("PRODUCT_NOT_FOUND", failureCode(result));
    }

    @Test
    void activatesDraftProductAndPublishesEvent() {
        ProductId productId = register("COFFEE-LATTE");

        events.reset();

        activateProduct.handle(
                new ActivateProductCommand(productId)
        ).await().indefinitely().orElseThrow();

        assertInstanceOf(
                ProductActivated.class,
                events.published().getFirst()
        );

        assertEquals(
                ProductStatus.ACTIVE,
                products.findById(productId)
                        .toCompletableFuture().join()
                        .orElseThrow().status()
        );
    }

    @Test
    void rejectsInvalidLifecycleTransition() {
        ProductId productId = register("COFFEE-LATTE");

        activateProduct.handle(
                new ActivateProductCommand(productId)
        ).await().indefinitely().orElseThrow();

        assertThrows(
                InvalidStateException.class,
                () -> activateProduct.handle(
                        new ActivateProductCommand(productId)
                ).await().indefinitely()
        );
    }

    @Test
    void renamesProductAndChangesDescription() {
        ProductId productId = register("COFFEE-LATTE");

        events.reset();

        updateProduct.handle(
                new UpdateProductCommand(
                        productId,
                        "Caffe Latte",
                        "Espresso with steamed milk"
                )
        ).await().indefinitely().orElseThrow();

        var updated = products.findById(productId)
                .toCompletableFuture().join()
                .orElseThrow();

        assertEquals("Caffe Latte", updated.name());
        assertEquals(
                "Espresso with steamed milk",
                updated.description()
        );

        assertInstanceOf(
                ProductRenamed.class,
                events.published().getFirst()
        );
    }

    @Test
    void addsProductIdentifier() {
        ProductId productId = register("MILK-1L");

        events.reset();

        addIdentifier.handle(
                new AddProductIdentifierCommand(
                        productId,
                        new ProductIdentifier(
                                ProductIdentifierType.EAN,
                                "8991234567890"
                        )
                )
        ).await().indefinitely().orElseThrow();

        assertInstanceOf(
                ProductIdentifierAdded.class,
                events.published().getFirst()
        );
    }

    @Test
    void followsFullLifecycleThroughHandlers() {
        ProductId productId = register("COFFEE-LATTE");

        activateProduct.handle(
                new ActivateProductCommand(productId)
        ).await().indefinitely().orElseThrow();

        events.reset();

        discontinueProduct.handle(
                new DiscontinueProductCommand(productId)
        ).await().indefinitely().orElseThrow();

        assertInstanceOf(
                ProductDiscontinued.class,
                events.published().getFirst()
        );

        events.reset();

        archiveProduct.handle(
                new ArchiveProductCommand(productId)
        ).await().indefinitely().orElseThrow();

        assertInstanceOf(
                ProductArchived.class,
                events.published().getFirst()
        );

        assertEquals(
                ProductStatus.ARCHIVED,
                products.findById(productId)
                        .toCompletableFuture().join()
                        .orElseThrow().status()
        );
    }


    @Test
    void normalizesWhitespaceInBarcode() {
        var productId = register("MILK-1L");

        addIdentifier.handle(
                new AddProductIdentifierCommand(
                        productId,
                        new ProductIdentifier(
                                ProductIdentifierType.GTIN,
                                " 123456789012 "  // leading/trailing spaces
                        )
                )
        ).await().indefinitely().orElseThrow();

        var stored = products.findById(productId)
                .toCompletableFuture().join().orElseThrow();
        var identifier = stored.identifiers().stream()
                .filter(i -> i.type() == ProductIdentifierType.GTIN)
                .findFirst().orElseThrow();

        assertEquals("123456789012", identifier.value());
    }

    @Test
    void stripsNonDigitCharactersForBarcodes() {
        var productId = register("MILK-1L");
        events.reset();

        addIdentifier.handle(
                new AddProductIdentifierCommand(
                        productId,
                        new ProductIdentifier(
                                ProductIdentifierType.EAN,
                                "89-9123-4567-890"  // dashes
                        )
                )
        ).await().indefinitely().orElseThrow();

        var stored = products.findById(productId)
                .toCompletableFuture().join().orElseThrow();
        var identifier = stored.identifiers().stream()
                .filter(i -> i.type() == ProductIdentifierType.EAN)
                .findFirst().orElseThrow();

        assertEquals("8991234567890", identifier.value());
    }

    @Test
    void rejectsInvalidEANLength() {
        var productId = register("MILK-1L");
        events.reset();

        var result = addIdentifier.handle(
                new AddProductIdentifierCommand(
                        productId,
                        new ProductIdentifier(
                                ProductIdentifierType.EAN,
                                "1234567"  // only 7 digits
                        )
                )
        ).await().indefinitely();

        assertInstanceOf(Result.Failure.class, result);
        assertEquals("BUSINESS_RULE_VIOLATION", failureCode(result));
    }

    @Test
    void rejectsDuplicateAfterNormalization() {
        var productId = register("MILK-1L");
        events.reset();

        addIdentifier.handle(
                new AddProductIdentifierCommand(
                        productId,
                        new ProductIdentifier(
                                ProductIdentifierType.EAN,
                                "8991234567890"
                        )
                )
        ).await().indefinitely().orElseThrow();

        var result = addIdentifier.handle(
                new AddProductIdentifierCommand(
                        productId,
                        new ProductIdentifier(
                                ProductIdentifierType.EAN,
                                " 8991234567890 "
                        )
                )
        ).await().indefinitely();

        assertInstanceOf(Result.Failure.class, result);
        assertEquals("BUSINESS_RULE_VIOLATION", failureCode(result));
    }
}
