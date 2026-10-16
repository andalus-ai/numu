package tech.kayys.syirkah.product.adapter.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.product.domain.product.Product;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.product.ProductType;
import tech.kayys.syirkah.product.domain.sku.Sku;
import tech.kayys.syirkah.product.domain.sku.SkuId;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifier;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifierType;

import tech.kayys.syirkah.product.domain.specification.AttributeDefinition;
import tech.kayys.syirkah.product.domain.specification.AttributeType;
import tech.kayys.syirkah.product.domain.specification.ProductSpecification;
import tech.kayys.syirkah.product.domain.specification.ProductSpecificationId;
import tech.kayys.syirkah.product.domain.variant.ProductVariant;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;
import tech.kayys.syirkah.product.domain.variant.VariantAttribute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("In-memory product repositories")
class InMemoryProductRepositoriesTest {

    private final InMemoryProductRepository products =
            new InMemoryProductRepository();

    private final InMemorySkuRepository skus = new InMemorySkuRepository();

    private final InMemoryProductVariantRepository variants =
            new InMemoryProductVariantRepository();

    private final InMemoryProductSpecificationRepository specifications =
            new InMemoryProductSpecificationRepository();

    @Test
    void savesAndFindsProductByCode() {
        var product = Product.create(
                ProductId.generate(),
                "COFFEE-LATTE",
                "Latte",
                null,
                ProductType.PHYSICAL
        );

        products.save(product).toCompletableFuture().join();

        assertTrue(
                products.existsByCode("COFFEE-LATTE")
                        .toCompletableFuture().join()
        );

        assertEquals(
                product.id(),
                products.findByCode("COFFEE-LATTE")
                        .toCompletableFuture().join()
                        .orElseThrow().id()
        );
    }

    @Test
    void deletingProductRemovesItFromLookups() {
        var product = Product.create(
                ProductId.generate(),
                "COFFEE-LATTE",
                "Latte",
                null,
                ProductType.PHYSICAL
        );

        products.save(product).toCompletableFuture().join();
        products.delete(product).toCompletableFuture().join();

        assertFalse(
                products.existsByCode("COFFEE-LATTE")
                        .toCompletableFuture().join()
        );

        assertTrue(
                products.findById(product.id())
                        .toCompletableFuture().join()
                        .isEmpty()
        );
    }

    @Test
    void savesAndFindsSkuByIdentifierAndProduct() {
        ProductId productId = ProductId.generate();

        var sku = Sku.create(
                SkuId.generate(),
                productId,
                null,
                "MILK-1L-0001",
                "Milk 1 litre"
        );

        sku.addIdentifier(
                new SkuIdentifier(
                        SkuIdentifierType.EAN,
                        "8991234567890"
                )
        );

        skus.save(sku).toCompletableFuture().join();

        assertTrue(
                skus.existsByCode("MILK-1L-0001")
                        .toCompletableFuture().join()
        );

        assertEquals(
                1,
                skus.findByProductId(productId)
                        .toCompletableFuture().join().size()
        );

        assertEquals(
                1,
                skus.findByCode("MILK-1L-0001")
                        .toCompletableFuture().join()
                        .orElseThrow().identifiers().size()
        );
    }

    @Test
    void savesAndFindsVariantByProductAndId() {
        ProductId productId = ProductId.generate();
        ProductVariantId variantId = ProductVariantId.generate();

        var variant = ProductVariant.create(
                variantId,
                productId,
                "VAR-RED-XL",
                "Extra Large Red"
        );
        variant.changeAttribute("color", "red");

        variants.save(variant).toCompletableFuture().join();

        assertTrue(
                variants.existsById(variantId)
                        .toCompletableFuture().join()
        );

        var foundList = variants.findByProductId(productId)
                .toCompletableFuture().join();
        assertEquals(1, foundList.size());
        assertEquals("VAR-RED-XL", foundList.getFirst().code());

        variants.deleteById(variantId).toCompletableFuture().join();
        assertFalse(
                variants.existsById(variantId)
                        .toCompletableFuture().join()
        );
    }

    @Test
    void savesAndFindsSpecificationByProductAndId() {
        ProductId productId = ProductId.generate();
        ProductSpecificationId specId = ProductSpecificationId.generate();

        var spec = ProductSpecification.create(
                specId,
                productId,
                "SPEC-001",
                "Tech Specs"
        );
        spec.addAttribute(new AttributeDefinition(
                "weight",
                "Weight",
                AttributeType.TEXT,
                false
        ));

        specifications.save(spec).toCompletableFuture().join();

        assertTrue(
                specifications.existsById(specId)
                        .toCompletableFuture().join()
        );

        var foundList = specifications.findByProductId(productId)
                .toCompletableFuture().join();
        assertEquals(1, foundList.size());
        assertEquals("Tech Specs", foundList.getFirst().name());

        specifications.delete(spec).toCompletableFuture().join();
        assertFalse(
                specifications.existsById(specId)
                        .toCompletableFuture().join()
        );
    }
}