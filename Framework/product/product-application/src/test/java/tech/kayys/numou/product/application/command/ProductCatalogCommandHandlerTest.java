package tech.kayys.syirkah.product.application.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.application.result.Result;
import tech.kayys.syirkah.product.application.support.InMemoryProductRepository;
import tech.kayys.syirkah.product.application.support.InMemoryProductSpecificationRepository;
import tech.kayys.syirkah.product.application.support.InMemoryProductVariantRepository;
import tech.kayys.syirkah.product.application.support.InMemorySkuRepository;
import tech.kayys.syirkah.product.application.support.RecordingEventPublisher;
import tech.kayys.syirkah.product.domain.event.ProductSpecificationChanged;
import tech.kayys.syirkah.product.domain.event.ProductVariantCreated;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.product.ProductType;
import tech.kayys.syirkah.product.domain.identifier.ProductIdentifier;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifier;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifierType;
import tech.kayys.syirkah.product.domain.specification.AttributeDefinition;
import tech.kayys.syirkah.product.domain.specification.AttributeType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Product catalog command handlers")
class ProductCatalogCommandHandlerTest {

    private final InMemoryProductRepository products =
            new InMemoryProductRepository();

    private final InMemoryProductVariantRepository variants =
            new InMemoryProductVariantRepository();

    private final InMemoryProductSpecificationRepository specifications =
            new InMemoryProductSpecificationRepository();

    private final InMemorySkuRepository skus = new InMemorySkuRepository();

    private final RecordingEventPublisher events =
            new RecordingEventPublisher();

    private final CreateProductHandler createProduct =
            new CreateProductHandler(products, events);

    private final CreateVariantHandler createVariant =
            new CreateVariantHandler(products, variants, events);

    private final CreateSpecificationHandler createSpecification =
            new CreateSpecificationHandler(
                    products,
                    specifications,
                    events
            );

    private final AddSpecificationAttributeHandler addAttribute =
            new AddSpecificationAttributeHandler(
                    specifications,
                    events
            );

    private final CreateSkuHandler createSku =
            new CreateSkuHandler(products, skus, events);

    private final AddSkuIdentifierHandler addSkuIdentifier =
            new AddSkuIdentifierHandler(skus, events);

    private final AddProductIdentifierHandler addProductIdentifier =
            new AddProductIdentifierHandler(products, events);

    private static String failureCode(Result<?> result) {
        return ((Result.Failure<?>) result).error().code();
    }

    private ProductId register(String code, ProductType type) {
        return createProduct.handle(
                new CreateProductCommand(code, code, null, type)
        ).await().indefinitely().orElseThrow();
    }

    @Test
    void createsVariantForExistingProduct() {
        ProductId productId = register("COFFEE", ProductType.PHYSICAL);

        events.reset();

        var variantId = createVariant.handle(
                new CreateVariantCommand(
                        productId,
                        "COFFEE-M",
                        "Coffee Medium"
                )
        ).await().indefinitely().orElseThrow();

        var created = assertInstanceOf(
                ProductVariantCreated.class,
                events.published().getFirst()
        );

        assertEquals(productId, created.productId());
        assertEquals(variantId, created.variantId());
    }

    @Test
    void reportsVariantCreationForMissingProduct() {
        var result = createVariant.handle(
                new CreateVariantCommand(
                        ProductId.generate(),
                        "COFFEE-M",
                        "Coffee Medium"
                )
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("PRODUCT_NOT_FOUND", failureCode(result));
    }

    @Test
    void buildsFnbSpecificationGenerically() {
        ProductId productId = register("COFFEE", ProductType.PHYSICAL);

        var specificationId = createSpecification.handle(
                new CreateSpecificationCommand(
                        productId,
                        "COFFEE-CONFIG",
                        "Coffee configuration"
                )
        ).await().indefinitely().orElseThrow();

        events.reset();

        addAttribute.handle(
                new AddSpecificationAttributeCommand(
                        specificationId,
                        new AttributeDefinition(
                                "sugar",
                                "Sugar level",
                                AttributeType.ENUM,
                                true
                        )
                )
        ).await().indefinitely().orElseThrow();

        assertInstanceOf(
                ProductSpecificationChanged.class,
                events.published().getFirst()
        );

        var specification = specifications.findById(specificationId)
                .toCompletableFuture().join()
                .orElseThrow();

        assertEquals(1, specification.attributes().size());
        assertEquals(
                AttributeType.ENUM,
                specification.attributes().getFirst().type()
        );
    }

    @Test
    void buildsSaasSpecificationWithoutDomainChanges() {
        ProductId productId = register(
                "SAAS-ACCOUNTING",
                ProductType.SUBSCRIPTION
        );

        var specificationId = createSpecification.handle(
                new CreateSpecificationCommand(
                        productId,
                        "SAAS-PLAN",
                        "SaaS plan specification"
                )
        ).await().indefinitely().orElseThrow();

        addAttribute.handle(
                new AddSpecificationAttributeCommand(
                        specificationId,
                        new AttributeDefinition(
                                "users",
                                "Included users",
                                AttributeType.NUMBER,
                                true
                        )
                )
        ).await().indefinitely().orElseThrow();

        var specification = specifications.findById(specificationId)
                .toCompletableFuture().join()
                .orElseThrow();

        assertEquals(1, specification.attributes().size());
    }

    @Test
    void createsSkuWithBarcode() {
        ProductId productId = register("MILK-1L", ProductType.PHYSICAL);

        events.reset();

        var skuId = createSku.handle(
                new CreateSkuCommand(
                        productId,
                        null,
                        "MILK-1L-0001",
                        "Milk 1 litre"
                )
        ).await().indefinitely().orElseThrow();

        assertInstanceOf(
                tech.kayys.syirkah.product.domain.event.SkuCreated.class,
                events.published().getFirst()
        );

        events.reset();

        addSkuIdentifier.handle(
                new AddSkuIdentifierCommand(
                        skuId,
                        new SkuIdentifier(
                                SkuIdentifierType.EAN,
                                "8991234567890"
                        )
                )
        ).await().indefinitely().orElseThrow();

        assertEquals(1, events.published().size());

        var sku = skus.findById(skuId)
                .toCompletableFuture().join()
                .orElseThrow();

        assertEquals(1, sku.identifiers().size());
    }

    @Test
    void rejectsDuplicateSkuCode() {
        ProductId productId = register("MILK-1L", ProductType.PHYSICAL);

        createSku.handle(
                new CreateSkuCommand(
                        productId,
                        null,
                        "MILK-1L-0001",
                        "Milk 1 litre"
                )
        ).await().indefinitely().orElseThrow();

        var result = createSku.handle(
                new CreateSkuCommand(
                        productId,
                        null,
                        "MILK-1L-0001",
                        "Milk 1 litre again"
                )
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("SKU_CODE_ALREADY_EXISTS", failureCode(result));
    }

    @Test
    void addsProductIdentifierNormalized() {
        ProductId productId = register("COFFEE-ESPRESSO", ProductType.PHYSICAL);

        events.reset();

        addProductIdentifier.handle(
                new AddProductIdentifierCommand(
                        productId,
                        new ProductIdentifier(
                                tech.kayys.syirkah.product.domain.identifier.ProductIdentifierType.GTIN,
                                " 8991234567890"  // normalized to 8991234567890
                        )
                )
        ).await().indefinitely().orElseThrow();

        assertEquals(1, events.published().size());

        var product = products.findById(productId)
                .toCompletableFuture().join()
                .orElseThrow();

        assertEquals(1, product.identifiers().size());
        assertEquals("8991234567890",
                product.identifiers().getFirst().value());
    }

    @Test
    void rejectsDuplicateProductIdentifierIdempotently() {
        ProductId productId = register("TEA-GREEN", ProductType.PHYSICAL);

        addProductIdentifier.handle(
                new AddProductIdentifierCommand(
                        productId,
                        new ProductIdentifier(
                                tech.kayys.syirkah.product.domain.identifier.ProductIdentifierType.EAN,
                                "8991234567890"
                        )
                )
        ).await().indefinitely().orElseThrow();

        events.reset();

        var result = addProductIdentifier.handle(
                new AddProductIdentifierCommand(
                        productId,
                        new ProductIdentifier(
                                tech.kayys.syirkah.product.domain.identifier.ProductIdentifierType.EAN,
                                "8991234567890"
                        )
                )
        ).await().indefinitely();

        assertTrue(result.isFailure());
        assertEquals("BUSINESS_RULE_VIOLATION", failureCode(result));
        assertEquals(0, events.published().size());
    }
}