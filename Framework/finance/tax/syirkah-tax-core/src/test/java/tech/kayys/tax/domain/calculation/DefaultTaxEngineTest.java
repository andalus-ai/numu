package tech.kayys.tax.domain.calculation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.kayys.syirkah.foundation.domain.valueobject.Currency;
import tech.kayys.syirkah.foundation.domain.valueobject.Money;
import tech.kayys.syirkah.foundation.domain.valueobject.MonetaryRoundingPolicy;
import tech.kayys.tax.domain.category.TaxTreatment;
import tech.kayys.tax.domain.policy.TaxDeterminationService;
import tech.kayys.tax.domain.policy.TaxPolicy;
import tech.kayys.tax.domain.policy.TaxPolicyStatus;
import tech.kayys.tax.domain.policy.TaxRate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultTaxEngineTest {

    private DefaultTaxEngine engine;
    private TaxPolicy ppn11Policy;
    private TaxPolicy exemptPolicy;

    @BeforeEach
    void setUp() {
        ppn11Policy = new TaxPolicy(
                "POL-PPN-11",
                "PPN11",
                "ID",
                "VAT",
                TaxTreatment.STANDARD_RATED,
                TaxRate.of(11),
                LocalDate.of(2025, 1, 1),
                null,
                TaxPolicyStatus.PUBLISHED,
                1
        );

        exemptPolicy = new TaxPolicy(
                "POL-EXEMPT",
                "EXEMPT",
                "ID",
                "VAT",
                TaxTreatment.EXEMPT,
                TaxRate.ZERO,
                LocalDate.of(2025, 1, 1),
                null,
                TaxPolicyStatus.PUBLISHED,
                1
        );

        TaxDeterminationService determinationService = context -> {
            if (context.preferredTreatment() == TaxTreatment.EXEMPT) {
                return Optional.of(exemptPolicy);
            }
            return Optional.of(ppn11Policy);
        };

        engine = new DefaultTaxEngine(determinationService);
    }

    @Test
    void testTaxExclusiveCalculation() {
        Currency idr = Currency.IDR();
        TaxDeterminationContext ctx = new TaxDeterminationContext(
                "TENANT-1", "ID", "VAT", TaxTreatment.STANDARD_RATED, LocalDate.now()
        );

        TaxCalculationLine line = new TaxCalculationLine(
                "LINE-1",
                Money.of(new BigDecimal("100000.00"), idr),
                ctx
        );

        TaxCalculationRequest req = new TaxCalculationRequest(
                "TXN-1",
                List.of(line),
                false,
                MonetaryRoundingPolicy.STANDARD_TWO_DECIMALS
        );

        TaxCalculationResult result = engine.calculate(req);

        assertEquals(new BigDecimal("100000.00"), result.totalTaxableBase().amount());
        assertEquals(new BigDecimal("11000.00"), result.totalTax().amount());
        assertEquals(1, result.lines().size());
        assertEquals("PPN11", result.lines().getFirst().policyCode());
    }

    @Test
    void testTaxInclusiveCalculation() {
        Currency idr = Currency.IDR();
        TaxDeterminationContext ctx = new TaxDeterminationContext(
                "TENANT-1", "ID", "VAT", TaxTreatment.STANDARD_RATED, LocalDate.now()
        );

        // Gross 111,000 -> Base 100,000 + Tax 11,000
        TaxCalculationLine line = new TaxCalculationLine(
                "LINE-1",
                Money.of(new BigDecimal("111000.00"), idr),
                ctx
        );

        TaxCalculationRequest req = new TaxCalculationRequest(
                "TXN-2",
                List.of(line),
                true,
                MonetaryRoundingPolicy.STANDARD_TWO_DECIMALS
        );

        TaxCalculationResult result = engine.calculate(req);

        assertEquals(new BigDecimal("100000.00"), result.totalTaxableBase().amount());
        assertEquals(new BigDecimal("11000.00"), result.totalTax().amount());
    }

    @Test
    void testExemptTreatment() {
        Currency idr = Currency.IDR();
        TaxDeterminationContext ctx = new TaxDeterminationContext(
                "TENANT-1", "ID", "VAT", TaxTreatment.EXEMPT, LocalDate.now()
        );

        TaxCalculationLine line = new TaxCalculationLine(
                "LINE-1",
                Money.of(new BigDecimal("100000.00"), idr),
                ctx
        );

        TaxCalculationRequest req = new TaxCalculationRequest(
                "TXN-3",
                List.of(line),
                false,
                MonetaryRoundingPolicy.STANDARD_TWO_DECIMALS
        );

        TaxCalculationResult result = engine.calculate(req);

        assertEquals(new BigDecimal("100000.00"), result.totalTaxableBase().amount());
        assertEquals(BigDecimal.ZERO, result.totalTax().amount());
        assertEquals(TaxTreatment.EXEMPT, result.lines().getFirst().treatment());
    }
}
