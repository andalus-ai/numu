package tech.kayys.syirkah.product.domain.variant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.product.domain.event.ProductVariantActivated;
import tech.kayys.syirkah.product.domain.event.ProductVariantCreated;
import tech.kayys.syirkah.product.domain.event.ProductVariantDiscontinued;
import tech.kayys.syirkah.product.domain.product.ProductId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Product variant aggregate")
class ProductVariantTest {

    private static ProductVariant newVariant(ProductId productId) {
        return ProductVariant.create(
                ProductVariantId.generate(),
                productId,
                "COFFEE-M",
                "Coffee Medium"
        );
    }

    @Test
    void createsVariantLinkedToProduct() {
        ProductId productId = ProductId.generate();

        var variant = newVariant(productId);

        assertEquals(productId, variant.productId());
        assertEquals("COFFEE-M", variant.code());
        assertEquals("Coffee Medium", variant.name());
        assertEquals(VariantStatus.DRAFT, variant.status());

        var created = assertInstanceOf(
                ProductVariantCreated.class,
                variant.pullDomainEvents().getFirst()
        );

        assertEquals(productId, created.productId());
        assertEquals("product.product-variant-created",
                created.eventType());
    }

    @Test
    void followsVariantLifecycle() {
        var variant = newVariant(ProductId.generate());

        variant.pullDomainEvents();

        variant.activate();
        assertEquals(VariantStatus.ACTIVE, variant.status());
        assertInstanceOf(
                ProductVariantActivated.class,
                variant.pullDomainEvents().getFirst()
        );

        variant.discontinue();
        assertEquals(VariantStatus.DISCONTINUED, variant.status());
        assertInstanceOf(
                ProductVariantDiscontinued.class,
                variant.pullDomainEvents().getFirst()
        );

        variant.archive();
        assertEquals(VariantStatus.ARCHIVED, variant.status());
    }

    @Test
    void rejectsActivatingTwice() {
        var variant = newVariant(ProductId.generate());

        variant.activate();

        assertThrows(InvalidStateException.class, variant::activate);
    }

    @Test
    void changesAndReplacesAttributes() {
        var variant = newVariant(ProductId.generate());

        variant.changeAttribute("color", "BLACK");
        variant.changeAttribute("size", "M");
        variant.changeAttribute("size", "L");

        assertEquals(2, variant.attributes().size());

        assertTrue(
                variant.attributes().stream().anyMatch(attribute ->
                        attribute.code().equals("size")
                                && attribute.value().equals("L"))
        );
    }

    @Test
    void removesAttribute() {
        var variant = newVariant(ProductId.generate());

        variant.changeAttribute("color", "BLACK");
        variant.removeAttribute("color");

        assertTrue(variant.attributes().isEmpty());
    }

    @Test
    void rejectsRemovingUnknownAttribute() {
        var variant = newVariant(ProductId.generate());

        assertThrows(
                BusinessRuleViolation.class,
                () -> variant.removeAttribute("color")
        );
    }

    @Test
    void archivedVariantCannotBeModified() {
        var variant = newVariant(ProductId.generate());

        variant.activate();
        variant.discontinue();
        variant.archive();

        assertThrows(
                InvalidStateException.class,
                () -> variant.changeAttribute("color", "BLACK")
        );
    }
}