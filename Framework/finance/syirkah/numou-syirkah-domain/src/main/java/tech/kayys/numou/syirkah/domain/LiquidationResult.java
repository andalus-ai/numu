package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Outcome of liquidation. {@code residual} = proceeds - debts; {@code netResult} = residual minus
 * all partner claims (capital + undistributed profit): positive is profit split by nisbah, negative is
 * loss split by capital. {@code payout} is what each partner finally receives (negative = owes).
 */
public record LiquidationResult(LiquidationInput input, Money residual, Money netResult,
                                List<AllocationLine> lines, List<Payout> payouts, Instant completedAt,
                                String hash) implements Serializable {

    public LiquidationResult {
        lines = List.copyOf(lines);
        payouts = List.copyOf(payouts);
    }

    /**
     * Versioned audit fingerprint over the complete liquidation outcome. Partner payouts and
     * allocation lines are material: changing either must invalidate the recorded result.
     */
    public static String computeHash(LiquidationInput input, Money residual, Money netResult,
                                     List<AllocationLine> lines, List<Payout> payouts, Instant completedAt) {
        StringBuilder canonical = new StringBuilder("liquidation-v2|")
                .append(input.assetProceeds().currency().code()).append(':')
                .append(input.assetProceeds().amount().stripTrailingZeros().toPlainString()).append('|')
                .append(input.externalDebts().currency().code()).append(':')
                .append(input.externalDebts().amount().stripTrailingZeros().toPlainString()).append('|')
                .append(input.shufahOffered()).append('|').append(input.liquidationDate()).append('|')
                .append(residual.currency().code()).append(':')
                .append(residual.amount().stripTrailingZeros().toPlainString()).append('|')
                .append(netResult.currency().code()).append(':')
                .append(netResult.amount().stripTrailingZeros().toPlainString()).append('|')
                .append(completedAt).append('|');
        lines.forEach(line -> canonical.append(line.canonical()).append(';'));
        canonical.append('|');
        payouts.forEach(payout -> canonical.append(payout.partner().value()).append(':')
                .append(payout.cashReceived().currency().code()).append(':')
                .append(payout.cashReceived().amount().stripTrailingZeros().toPlainString()).append(':')
                .append(payout.owed().currency().code()).append(':')
                .append(payout.owed().amount().stripTrailingZeros().toPlainString()).append(';'));
        return AuditHash.sha256(canonical.toString());
    }

    public boolean hashIsValid() {
        return hash.equals(computeHash(input, residual, netResult, lines, payouts, completedAt));
    }

    /**
     * Unfunded liquidation loss remaining after eligible partner capital and
     * any evidenced manager-fault liability have been accounted for.
     */
    public Money unallocatedLoss() {
        Money zero = Money.zero(netResult.currency());
        if (!netResult.isNegative()) {
            return zero;
        }
        Money allocated = zero;
        for (AllocationLine line : lines) {
            allocated = allocated.add(line.lossBorne()).add(line.managerLiability());
        }
        Money excess = netResult.negate().subtract(allocated);
        return excess.isPositive() ? excess : zero;
    }

    public record Payout(PartnerId partner, Money cashReceived, Money owed) implements Serializable {
    }
}
