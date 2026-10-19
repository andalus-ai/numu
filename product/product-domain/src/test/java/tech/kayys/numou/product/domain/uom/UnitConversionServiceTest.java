package tech.kayys.numou.product.domain.uom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UnitConversionServiceTest {

    private DefaultUnitConversionService service;
    private UnitOfMeasure box;
    private UnitOfMeasure pcs;
    private UnitOfMeasure kg;
    private UnitOfMeasure g;
    private UnitOfMeasure liter;

    @BeforeEach
    void setUp() {
        service = new DefaultUnitConversionService();
        box = new UnitOfMeasure("BOX", "Box", UnitCategory.COUNT);
        pcs = new UnitOfMeasure("PCS", "Pieces", UnitCategory.COUNT);
        kg = new UnitOfMeasure("KG", "Kilogram", UnitCategory.WEIGHT);
        g = new UnitOfMeasure("G", "Gram", UnitCategory.WEIGHT);
        liter = new UnitOfMeasure("L", "Liter", UnitCategory.VOLUME);

        // 1 BOX = 12 PCS
        service.register(new UnitConversion(box, pcs, new BigDecimal("12")));
        // 1 KG = 1000 G
        service.register(new UnitConversion(kg, g, new BigDecimal("1000")));
    }

    @Test
    void testDirectConversion() {
        Quantity source = Quantity.of(new BigDecimal("2"), box);
        Quantity result = service.convert(source, pcs, new RoundingPolicy(2, RoundingMode.HALF_UP));

        assertEquals(new BigDecimal("24.00"), result.amount());
        assertEquals(pcs, result.unit());
    }

    @Test
    void testReciprocalConversion() {
        Quantity source = Quantity.of(new BigDecimal("24"), pcs);
        Quantity result = service.convert(source, box, new RoundingPolicy(2, RoundingMode.HALF_UP));

        assertEquals(new BigDecimal("2.00"), result.amount());
        assertEquals(box, result.unit());
    }

    @Test
    void testReciprocalWithRounding() {
        Quantity source = Quantity.of(new BigDecimal("10"), pcs);
        // 10 / 12 = 0.8333...
        Quantity result = service.convert(source, box, new RoundingPolicy(4, RoundingMode.HALF_UP));

        assertEquals(new BigDecimal("0.8333"), result.amount());
        assertEquals(box, result.unit());
    }

    @Test
    void testDimensionMismatchThrows() {
        Quantity source = Quantity.of(new BigDecimal("5"), kg);
        assertThrows(IllegalArgumentException.class, () ->
                service.convert(source, liter, new RoundingPolicy(2, RoundingMode.HALF_UP))
        );
    }

    @Test
    void testMissingConversionThrows() {
        UnitOfMeasure mg = new UnitOfMeasure("MG", "Milligram", UnitCategory.WEIGHT);
        Quantity source = Quantity.of(new BigDecimal("5"), kg);
        assertThrows(IllegalArgumentException.class, () ->
                service.convert(source, mg, new RoundingPolicy(2, RoundingMode.HALF_UP))
        );
    }

    @Test
    void testQuantityArithmetic() {
        Quantity q1 = Quantity.of(new BigDecimal("10.5"), kg);
        Quantity q2 = Quantity.of(new BigDecimal("4.5"), kg);

        assertEquals(new BigDecimal("15.0"), q1.add(q2).amount());
        assertEquals(new BigDecimal("6.0"), q1.subtract(q2).amount());
        assertEquals(new BigDecimal("21.0"), q1.multiply(2).amount());
    }

    @Test
    void testPackagingConversion() {
        PackagingConversion packaging = new PackagingConversion(box, pcs, 12);
        assertEquals(24, packaging.toUnits(2));
        assertEquals(box, packaging.from());
        assertEquals(pcs, packaging.to());
    }

    @Test
    void testProductUnitPolicy() {
        PackagingConversion pkg = new PackagingConversion(box, pcs, 12);
        ProductUnitPolicy policy = new ProductUnitPolicy(
                "PROD-001",
                pcs,
                Set.of(pcs, box),
                Set.of(box),
                Set.of(pkg)
        );

        assertTrue(policy.isSalesUnitPermitted(pcs));
        assertTrue(policy.isSalesUnitPermitted(box));
        assertFalse(policy.isPurchaseUnitPermitted(pcs));
        assertTrue(policy.isPurchaseUnitPermitted(box));
    }
}
