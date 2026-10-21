package tech.kayys.numou.mobility.wallet.application;

import tech.kayys.numou.mobility.wallet.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A driver's statement for a period, built purely from the wallet ledger (the money that actually moved), so it can never disagree with the
 * balance. Entries are classified by the reference the operation carried; holds and releases do not move money and are ignored.
 *
 * <p>Cash trips appear as {@code CASH_COMMISSION}: the driver collected the fare in cash and owes the platform its share, so the line is
 * negative. {@code net} is the sum of every category and equals {@code closingBalance - openingBalance}.
 */
public record EarningsStatement(Instant from, Instant to, Map<Category, BigDecimal> totals, int trips, BigDecimal net,
                                BigDecimal openingBalance, BigDecimal closingBalance) {

    public enum Category { RIDE_EARNINGS, CASH_COMMISSION, TIPS, CANCELLATION_FEES, INCENTIVES, REFUND_ADJUSTMENTS, PAYOUTS, PAYOUT_REVERSALS, OTHER }

    public static EarningsStatement build(List<LedgerEntry> entries, Instant from, Instant to, int fractionDigits) {
        final var totals = new EnumMap<Category, BigDecimal>(Category.class);
        for (final var c : Category.values()) totals.put(c, BigDecimal.ZERO.setScale(fractionDigits));
        final Set<String> rides = new HashSet<>();
        BigDecimal opening = BigDecimal.ZERO.setScale(fractionDigits), closing = opening, net = opening;
        boolean first = true;
        for (final var e : entries.stream().sorted(java.util.Comparator.comparingLong(LedgerEntry::seq)).toList()) {
            if (e.kind() == LedgerEntry.Kind.HOLD || e.kind() == LedgerEntry.Kind.RELEASE) continue;
            if (first) {
                opening = e.balanceAfter().subtract(e.amount());
                first = false;
            }
            closing = e.balanceAfter();
            final Category c = classify(e);
            totals.merge(c, e.amount(), BigDecimal::add);
            net = net.add(e.amount());
            if (c == Category.RIDE_EARNINGS || c == Category.CASH_COMMISSION) rides.add(rideOf(e.ref()));
        }
        return new EarningsStatement(from, to, Map.copyOf(totals), rides.size(), net, opening, closing);
    }

    static Category classify(LedgerEntry e) {
        final String ref = e.ref();
        if (ref.contains(":settle:driver")) return e.amount().signum() >= 0 ? Category.RIDE_EARNINGS : Category.CASH_COMMISSION;
        if (ref.contains(":tip:driver")) return Category.TIPS;
        if (ref.contains(":fee:driver")) return Category.CANCELLATION_FEES;
        if (ref.startsWith("payout-reversal:")) return Category.PAYOUT_REVERSALS;
        if (ref.startsWith("payout:")) return Category.PAYOUTS;
        if (ref.startsWith("refund:")) return Category.REFUND_ADJUSTMENTS;
        if (ref.startsWith("incentive:")) return Category.INCENTIVES;
        return Category.OTHER;
    }

    private static String rideOf(String ref) {
        final String[] p = ref.split(":");
        return p.length > 1 ? p[1] : ref;
    }
}
