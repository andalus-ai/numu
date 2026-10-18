package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps partner movements to balanced double-entry lines using abstract account roles; the
 * application layer binds roles to chart-of-accounts codes. Negative amounts (reversals) flip sides.
 */
public final class SyirkahPostingPlan {

    public enum AccountRole {
        CASH, PARTNER_CAPITAL, PROFIT_PAYABLE, PERIOD_RESULT, PARTNER_RECEIVABLE
    }

    public record Line(AccountRole account, Money debit, Money credit, PartnerId partner) {
    }

    private SyirkahPostingPlan() {
    }

    public static List<Line> forMovement(PartnerMovement m) {
        AccountRole debit;
        AccountRole credit;
        switch (m.type()) {
            case CONTRIBUTION -> { debit = AccountRole.CASH; credit = AccountRole.PARTNER_CAPITAL; }
            case CAPITAL_RETURN -> { debit = AccountRole.PARTNER_CAPITAL; credit = AccountRole.CASH; }
            case LOSS_ALLOCATED -> { debit = AccountRole.PARTNER_CAPITAL; credit = AccountRole.PERIOD_RESULT; }
            case CAPITAL_RESTORED -> { debit = AccountRole.PERIOD_RESULT; credit = AccountRole.PARTNER_CAPITAL; }
            case PROFIT_ALLOCATED -> { debit = AccountRole.PERIOD_RESULT; credit = AccountRole.PROFIT_PAYABLE; }
            case PROFIT_RETAINED -> { debit = AccountRole.PROFIT_PAYABLE; credit = AccountRole.PARTNER_CAPITAL; }
            case PROFIT_DISTRIBUTED -> { debit = AccountRole.PROFIT_PAYABLE; credit = AccountRole.CASH; }
            case MANAGER_LOSS_LIABILITY -> { debit = AccountRole.PARTNER_RECEIVABLE; credit = AccountRole.PERIOD_RESULT; }
            case LIABILITY_SETTLED -> { debit = AccountRole.CASH; credit = AccountRole.PARTNER_RECEIVABLE; }
            case DEFICIT_TO_LIABILITY -> { debit = AccountRole.PARTNER_RECEIVABLE; credit = AccountRole.PARTNER_CAPITAL; }
            case OVERPAYMENT_TO_LIABILITY -> { debit = AccountRole.PARTNER_RECEIVABLE; credit = AccountRole.PROFIT_PAYABLE; }
            case BASIS_CLOSED -> { return List.of(); }
            default -> throw new IllegalStateException("unmapped movement " + m.type());
        }
        Money abs = m.amount().abs();
        Money zero = Money.zero(abs.currency());
        List<Line> lines = new ArrayList<>();
        if (m.amount().isNegative()) {
            lines.add(new Line(credit, abs, zero, m.partner()));
            lines.add(new Line(debit, zero, abs, m.partner()));
        } else {
            lines.add(new Line(debit, abs, zero, m.partner()));
            lines.add(new Line(credit, zero, abs, m.partner()));
        }
        return lines;
    }

    public static List<Line> forMovements(List<PartnerMovement> movements) {
        List<Line> all = new ArrayList<>();
        movements.forEach(m -> all.addAll(forMovement(m)));
        return all;
    }

    /** Idempotency key for the journal produced from one movement. */
    public static String sourceKey(SyirkahId id, PartnerMovement m) {
        return "syirkah:" + id.value() + ":movement:" + m.sequence();
    }
}
