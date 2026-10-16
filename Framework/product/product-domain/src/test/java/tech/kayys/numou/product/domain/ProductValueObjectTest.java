package tech.kayys.syirkah.product.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.product.domain.bundle.BundleComponent;
import tech.kayys.syirkah.product.domain.identifier.ProductIdentifierType;
import tech.kayys.syirkah.product.domain.identifier.ProductIdentifier;
import tech.kayys.syirkah.product.domain.packaging.Packaging;
import tech.kayys.syirkah.product.domain.product.ProductId;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifier;
import tech.kayys.syirkah.product.domain.sku.SkuIdentifierType;
import tech.kayys.syirkah.product.domain.uom.UnitCategory;
import tech.kayys.syirkah.product.domain.uom.UnitConversion;
import tech.kayys.syirkah.product.domain.uom.UnitOfMeasure;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Product value objects")
class ProductValueObjectTest {

    private static final UnitOfMeasure PCS =
            new UnitOfMeasure("pcs", "Pieces", UnitCategory.COUNT);

    private static final UnitOfMeasure BOX =
            new UnitOfMeasure("BOX", "Box", UnitCategory.COUNT);

    private static final UnitOfMeasure KG =
            new UnitOfMeasure("KG", "Kilogram", UnitCategory.WEIGHT);

    @Test
    void normalizesUnitCodeToUpperCase() {
        assertEquals("PCS", PCS.code());
        assertEquals("Pieces", PCS.name());
    }

    @Test
    void rejectsInvalidUnit() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UnitOfMeasure(" ", "Pieces", UnitCategory.COUNT)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new UnitOfMeasure("PCS", "Pieces", null)
        );
    }

    @Test
    void convertsWithinSameCategory() {
        var conversion = new UnitConversion(
                BOX,
                PCS,
                BigDecimal.valueOf(12)
        );

        assertEquals(
                0,
                conversion.convert(BigDecimal.valueOf(2))
                        .compareTo(BigDecimal.valueOf(24))
        );
    }

    @Test
    void rejectsCrossCategoryConversion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UnitConversion(
                        KG,
                        PCS,
                        BigDecimal.valueOf(12)
                )
        );
    }

    @Test
    void rejectsSelfConversionAndNonPositiveFactor() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new UnitConversion(
                        PCS,
                        PCS,
                        BigDecimal.ONE
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new UnitConversion(
                        BOX,
                        PCS,
                        BigDecimal.ZERO
                )
        );
    }

    @Test
    void packagingRequiresPositiveQuantity() {
        var packaging = new Packaging(
                "BOX-12",
                "Box of 12",
                PCS,
                BigDecimal.valueOf(12)
        );

        assertEquals("BOX-12", packaging.code());

        assertThrows(
                IllegalArgumentException.class,
                () -> new Packaging(
                        "BOX-0",
                        "Empty box",
                        PCS,
                        BigDecimal.ZERO
                )
        );
    }

    @Test
    void bundleComponentRequiresPositiveQuantity() {
        var component = new BundleComponent(
                ProductId.generate(),
                BigDecimal.ONE
        );

        assertEquals(
                0,
                component.quantity().compareTo(BigDecimal.ONE)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new BundleComponent(
                        ProductId.generate(),
                        BigDecimal.valueOf(-1)
                )
        );
    }

    @Test
    void productIdentifierValidatesValueAndNamespace() {
        var identifier = new ProductIdentifier(
                ProductIdentifierType.SUPPLIER_CODE,
                " SUP-991 ",
                tech.kayys.syirkah.product.domain.identifier.ProductIdentifierScope.SUPPLIER
        );

        assertEquals("SUP-991", identifier.value());
        assertEquals("SUPPLIER", identifier.scope().name());

        var namespaceless = new ProductIdentifier(
                ProductIdentifierType.EAN,
                "8991234567890"
        );

        assertEquals(
                tech.kayys.syirkah.product.domain.identifier.ProductIdentifierScope.GLOBAL,
                namespaceless.scope());

        assertThrows(
                IllegalArgumentException.class,
                () -> new ProductIdentifier(
                        ProductIdentifierType.EAN,
                        " "
                )
        );
    }

    @Test
    void skuIdentifierValidatesInput() {
        var identifier = new SkuIdentifier(
                SkuIdentifierType.GTIN,
                "1234567890123"
        );

        assertEquals(SkuIdentifierType.GTIN, identifier.type());

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkuIdentifier(null, "123")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new SkuIdentifier(
                        SkuIdentifierType.EAN,
                        "  "
                )
        );
    }
}