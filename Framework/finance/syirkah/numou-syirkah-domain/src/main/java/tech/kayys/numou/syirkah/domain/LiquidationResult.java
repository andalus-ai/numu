package tech.kayys.numou.syirkah.domain;

import tech.kayys.numou.foundation.domain.valueobject.Money;

import java.io.Serializable;
import java.time.Instant;
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

    public record Payout(PartnerId partner, Money cashReceived, Money owed) implements Serializable {
    }
}
