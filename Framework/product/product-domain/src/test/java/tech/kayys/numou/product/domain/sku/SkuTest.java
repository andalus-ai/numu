package tech.kayys.syirkah.product.domain.sku;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.syirkah.foundation.domain.exception.InvalidStateException;
import tech.kayys.syirkah.product.domain.event.SkuActivated;
import tech.kayys.syirkah.product.domain.event.SkuCreated;
import tech.kayys.syirkah.product.domain.event.SkuIdentifierAdded;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.variant.ProductVariantId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("SKU aggregate")
class SkuTest {

    private static Sku newSku(ProductVariantId variantId) {
        return Sku.create(
                SkuId.generate(),
                ProductId.generate(),
                variantId,
                "AM90-BLK-42",
                "Air Max 90 Black 42"
        );
    }

    @Test
    void createsSkuLinkedToProductAndVariant() {
        ProductId productId = ProductId.generate();
        ProductVariantId variantId = ProductVariantId.generate();

        var sku = Sku.create(
                SkuId.generate(),
                productId,
                variantId,
                "AM90-BLK-42",
                "Air Max 90 Black 42"
        );

        assertEquals(productId, sku.productId());
        assertEquals(variantId, sku.variantId());
        assertEquals(SkuStatus.DRAFT, sku.status());

        var created = assertInstanceOf(
                SkuCreated.class,
                sku.pullDomainEvents().getFirst()
        );

        assertEquals(variantId, created.variantId());
        assertEquals("product.sku-created", created.eventType());
    }

    @Test
    void variantIsOptional() {
        var sku = newSku(null);

        assertNull(sku.variantId());
    }

    @Test
    void followsSkuLifecycle() {
        var sku = newSku(null);

        sku.pullDomainEvents();

        sku.activate();
        assertEquals(SkuStatus.ACTIVE, sku.status());
        assertInstanceOf(
                SkuActivated.class,
                sku.pullDomainEvents().getFirst()
        );

        sku.discontinue();
        assertEquals(SkuStatus.DISCONTINUED, sku.status());

        sku.archive();
        assertEquals(SkuStatus.ARCHIVED, sku.status());
    }

    @Test
    void rejectsActivatingTwice() {
        var sku = newSku(null);

        sku.activate();

        assertThrows(InvalidStateException.class, sku::activate);
    }

    @Test
    void addsIdentifierAndRejectsDuplicate() {
        var sku = newSku(null);

        sku.pullDomainEvents();

        var ean = new SkuIdentifier(
                SkuIdentifierType.EAN,
                "8991234567890"
        );

        sku.addIdentifier(ean);

        assertEquals(1, sku.identifiers().size());
        assertInstanceOf(
                SkuIdentifierAdded.class,
                sku.pullDomainEvents().getFirst()
        );

        assertThrows(
                BusinessRuleViolation.class,
                () -> sku.addIdentifier(ean)
        );
    }

    @Test
    void archivedSkuCannotBeModified() {
        var sku = newSku(null);

        sku.activate();
        sku.discontinue();
        sku.archive();

        assertThrows(
                InvalidStateException.class,
                () -> sku.addIdentifier(
                        new SkuIdentifier(
                                SkuIdentifierType.EAN,
                                "8991234567890"
                        )
                )
        );

        assertThrows(
                InvalidStateException.class,
                () -> sku.rename("Renamed")
        );
    }
}