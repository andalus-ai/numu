package tech.kayys.syirkah.accounting.consolidation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ConsolidationEngineTest {

    @Test
    void testOwnershipEngineEffectiveOwnership() {
        OwnershipEngine engine = new OwnershipEngine();
        // Parent owns 80% of HoldCo, HoldCo owns 75% of Sub -> Effective = 60%
        BigDecimal effective = engine.calculateEffectiveOwnership(new BigDecimal("0.80"), new BigDecimal("0.75"));
        assertEquals(0, new BigDecimal("0.600000").compareTo(effective));

        BigDecimal nci = engine.calculateNciPercentage(effective);
        assertEquals(0, new BigDecimal("0.400000").compareTo(nci));
    }

    @Test
    void testTranslationEngineAndCta() {
        TranslationEngine engine = new TranslationEngine();
        BigDecimal amount = new BigDecimal("1000.00");
        BigDecimal rate = new BigDecimal("1.10");
        BigDecimal translated = engine.translate(amount, rate);
        assertEquals(new BigDecimal("1100.0000"), translated);

        // CTA on 50,000 net assets when rate changed from 1.05 to 1.12
        BigDecimal cta = engine.calculateCta(new BigDecimal("50000"), new BigDecimal("1.05"), new BigDecimal("1.12"));
        assertEquals(new BigDecimal("3500.0000"), cta);
    }

    @Test
    void testEliminationRules() {
        ReciprocalEliminationRule reciprocalRule = new ReciprocalEliminationRule();
        var ctx = new EliminationRule.ConsolidationContext(
                "CompanyA", "CompanyB", "1200", "2000",
                new BigDecimal("5000.00"), new BigDecimal("4800.00"), "USD"
        );
        Optional<ConsolidationRun.Elimination> elim = reciprocalRule.evaluate(ctx);
        assertTrue(elim.isPresent());
        assertEquals(new BigDecimal("4800.00"), elim.get().amount());

        UnrealizedProfitEliminationRule profitRule = new UnrealizedProfitEliminationRule(new BigDecimal("0.25"));
        var invCtx = new EliminationRule.ConsolidationContext(
                "CompanyA", "CompanyB", "1400", "5000",
                new BigDecimal("10000.00"), null, "USD"
        );
        Optional<ConsolidationRun.Elimination> profitElim = profitRule.evaluate(invCtx);
        assertTrue(profitElim.isPresent());
        assertEquals(new BigDecimal("2500.0000"), profitElim.get().amount());
    }

    @Test
    void testMinorityInterestAttribution() {
        MinorityInterestEngine engine = new MinorityInterestEngine();
        var attribution = engine.attribute(new BigDecimal("100000.00"), new BigDecimal("0.70"));
        assertEquals(new BigDecimal("70000.0000"), attribution.parentShare());
        assertEquals(new BigDecimal("30000.0000"), attribution.nciShare());
    }
}
