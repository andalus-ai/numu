package tech.kayys.numou.numoudomain;

import tech.kayys.numou.foundation.domain.valueobject.Currency;
import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The financial statements in the layout of Hukum Muamalah Bab XIX: the balance sheet (neraca: aktiva on one side,
 * kewajiban and modal on the other, always equal) and the profit and loss statement (laporan rugi laba: revenue,
 * costs, profit or loss), each with the title the book asks for (name of the syirkah, name of the statement, date or
 * period).
 *
 * <p>The ledger knows the equity side. It does not hold the syirkah's assets or its debts to outsiders, so the caller
 * supplies those lines (from the accounting records). What the ledger can check is that the two sides agree, and the
 * balance sheet reports the difference instead of hiding it.
 */
public final class BookStatements {

    private BookStatements() {
    }

    public enum AssetGroup { CURRENT, FIXED }

    public enum LiabilityGroup { SHORT_TERM, LONG_TERM }

    public record AssetLine(AssetGroup group, String label, Money amount) {
        public AssetLine {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(amount, "amount");
        }
    }

    public record LiabilityLine(LiabilityGroup group, String label, Money amount) {
        public LiabilityLine {
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(amount, "amount");
        }
    }

    public record CapitalLine(PartnerId partner, String name, Money capital, Money profitPayable) {
    }

    /**
     * @param assets                  the caller's asset lines plus, from the ledger, what partners owe the syirkah
     * @param partnerReceivable       what partners owe the syirkah (manager fault, deficit); included in {@code assets}
     * @param unallocatedDeficit      losses beyond partners' capital, shown as negative capital
     * @param difference              total assets less total liabilities and capital; zero when the sides agree
     */
    public record BalanceSheet(String syirkahName, LocalDate asOf, List<AssetLine> assets, Money partnerReceivable,
                               Money totalAssets, List<LiabilityLine> liabilities, Money totalLiabilities,
                               List<CapitalLine> capital, Money unallocatedDeficit, Money totalCapital,
                               Money totalLiabilitiesAndCapital, Money difference) {
        public boolean balanced() {
            return difference.isZero();
        }
    }

    public record IncomeStatement(String syirkahName, java.time.LocalDate periodStart, java.time.LocalDate periodEnd,
                                  Money revenue, Money costOfGoodsSold, Money grossProfit, Money operatingCosts,
                                  Money otherCosts, Money netResult, String result) {
    }

    /**
     * Balance sheet at the present state of the ledger. {@code assets} are all the syirkah's assets except what
     * partners owe it, which the ledger adds itself; {@code liabilities} are debts to outsiders. Profit allocated to
     * partners and not yet paid or retained is shown with their capital.
     */
    public static BalanceSheet balanceSheet(Syirkah syirkah, String syirkahName, LocalDate asOf,
                                            List<AssetLine> assets, List<LiabilityLine> liabilities) {
        Objects.requireNonNull(syirkah, "syirkah");
        Objects.requireNonNull(asOf, "asOf");
        Currency cur = syirkah.contract().currency();
        Money zero = Money.zero(cur);

        List<AssetLine> allAssets = new ArrayList<>(assets == null ? List.<AssetLine>of() : assets);
        Money owed = zero;
        List<CapitalLine> capital = new ArrayList<>();
        Money totalCapital = zero;
        for (var e : syirkah.positions().entrySet()) {
            PartnerPosition p = e.getValue();
            owed = owed.add(p.liability());
            capital.add(new CapitalLine(e.getKey(), syirkah.partnerName(e.getKey()), p.balance(), p.payable()));
            totalCapital = totalCapital.add(p.balance()).add(p.payable());
        }
        if (owed.isPositive()) {
            allAssets.add(new AssetLine(AssetGroup.CURRENT, "Receivable from partners", owed));
        }
        Money deficit = zero;
        for (CloseSnapshot c : syirkah.closes()) {
            if (c.status() == CloseStatus.ACTIVE) {
                deficit = deficit.add(c.unallocatedLoss());
            }
        }
        if (syirkah.liquidation() != null) {
            deficit = deficit.add(syirkah.liquidation().unallocatedLoss());
        }
        totalCapital = totalCapital.subtract(deficit);

        Money totalAssets = sumAssets(allAssets, zero);
        List<LiabilityLine> liab = liabilities == null ? List.of() : List.copyOf(liabilities);
        Money totalLiab = zero;
        for (LiabilityLine l : liab) {
            totalLiab = totalLiab.add(l.amount());
        }
        Money claims = totalLiab.add(totalCapital);
        return new BalanceSheet(syirkahName, asOf, List.copyOf(allAssets), owed, totalAssets, liab, totalLiab,
                List.copyOf(capital), deficit, totalCapital, claims, totalAssets.subtract(claims));
    }

    /** Profit and loss statement of one close, in the book's order: revenue, costs, profit or loss. */
    public static IncomeStatement incomeStatement(String syirkahName, CloseSnapshot close) {
        Objects.requireNonNull(close, "close");
        FinancialResult r = close.result();
        Money gross = r.revenue().subtract(r.directCosts());
        Money net = close.netResult();
        String label = net.isPositive() ? "PROFIT" : net.isZero() ? "BREAK-EVEN" : "LOSS";
        return new IncomeStatement(syirkahName, close.periodStart(), close.periodEnd(), r.revenue(), r.directCosts(),
                gross, r.operatingCosts(), r.otherCosts(), net, label);
    }

    private static Money sumAssets(List<AssetLine> lines, Money zero) {
        Money t = zero;
        for (AssetLine a : lines) {
            t = t.add(a.amount());
        }
        return t;
    }
}
