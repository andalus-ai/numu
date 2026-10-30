package tech.kayys.numou.numoudomain;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static tech.kayys.numou.numoudomain.Fixtures.*;

/**
 * Guards the accounting mapping against silent mistakes: for every movement type the journal lines must balance, and
 * their effect on the capital, profit-payable and partner-receivable accounts must agree with the signs the movement
 * type declares. Changing one without the other fails here.
 */
class SyirkahPostingPlanTest {

    private static PartnerMovement movement(MovementType t, long amount) {
        return PartnerMovement.create(1, A, t, idr(amount), CloseId.generate(), "ref", NOW, null);
    }

    private static Map<SyirkahPostingPlan.AccountRole, BigDecimal> debitMinusCredit(List<SyirkahPostingPlan.Line> lines) {
        Map<SyirkahPostingPlan.AccountRole, BigDecimal> m = new EnumMap<>(SyirkahPostingPlan.AccountRole.class);
        for (var l : lines) {
            m.merge(l.account(), l.debit().amount().subtract(l.credit().amount()), BigDecimal::add);
        }
        return m;
    }

    private static BigDecimal of(Map<SyirkahPostingPlan.AccountRole, BigDecimal> m, SyirkahPostingPlan.AccountRole r) {
        return m.getOrDefault(r, BigDecimal.ZERO);
    }

    @Test
    void everyMovementTypeIsMappedAndBalances() {
        for (MovementType t : MovementType.values()) {
            for (long amount : new long[]{100, -100}) {
                List<SyirkahPostingPlan.Line> lines = SyirkahPostingPlan.forMovement(movement(t, amount));
                BigDecimal net = debitMinusCredit(lines).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                assertEquals(0, net.signum(), t + " " + amount + " does not balance");
                if (t == MovementType.BASIS_CLOSED) {
                    assertTrue(lines.isEmpty());
                } else {
                    assertEquals(2, lines.size(), t.toString());
                }
            }
        }
    }

    @Test
    void accountEffectsAgreeWithTheDeclaredSigns() {
        for (MovementType t : MovementType.values()) {
            if (t == MovementType.BASIS_CLOSED) {
                continue;
            }
            for (int dir : new int[]{1, -1}) {
                var m = debitMinusCredit(SyirkahPostingPlan.forMovement(movement(t, 100L * dir)));
                BigDecimal unit = BigDecimal.valueOf(100L * dir);
                // equity accounts grow on credit, the receivable grows on debit
                assertEquals(0, unit.multiply(BigDecimal.valueOf(t.capitalSign()))
                        .compareTo(of(m, SyirkahPostingPlan.AccountRole.PARTNER_CAPITAL).negate()),
                        t + " capital effect");
                assertEquals(0, unit.multiply(BigDecimal.valueOf(t.payableSign()))
                        .compareTo(of(m, SyirkahPostingPlan.AccountRole.PROFIT_PAYABLE).negate()),
                        t + " profit payable effect");
                assertEquals(0, unit.multiply(BigDecimal.valueOf(t.liabilitySign()))
                        .compareTo(of(m, SyirkahPostingPlan.AccountRole.PARTNER_RECEIVABLE)),
                        t + " partner receivable effect");
            }
        }
    }

    @Test
    void linesCarryThePartnerAndSourceKeysAreUniquePerMovement() {
        SyirkahId id = SyirkahId.generate();
        Set<String> keys = new HashSet<>();
        for (int i = 1; i <= 5; i++) {
            PartnerMovement m = PartnerMovement.create(i, B, MovementType.CONTRIBUTION, idr(10), null, "r" + i, NOW,
                    null);
            assertTrue(keys.add(SyirkahPostingPlan.sourceKey(id, m)));
            SyirkahPostingPlan.forMovement(m).forEach(l -> assertEquals(B, l.partner()));
        }
        Money zero = idr(0);
        assertEquals(zero.currency(), SyirkahPostingPlan.forMovement(movement(MovementType.CONTRIBUTION, 5)).get(0)
                .debit().currency());
    }
}
