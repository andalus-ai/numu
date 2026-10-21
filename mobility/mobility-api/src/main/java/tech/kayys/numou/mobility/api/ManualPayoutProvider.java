package tech.kayys.numou.mobility.api;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation;
import tech.kayys.numou.mobility.wallet.domain.Payout;
import tech.kayys.numou.mobility.wallet.spi.PayoutProvider;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Payouts settled by finance in a bank batch (a common way to start): the sweeper marks requests SUBMITTED, finance lists them
 * ({@code GET /ops/payouts?status=SUBMITTED}), transfers, and records each result ({@code POST /ops/payouts/{id}/result}); a failed
 * result returns the money to the driver's wallet exactly once. Replace with a disbursement-API adapter to automate.
 */
public final class ManualPayoutProvider implements PayoutProvider {

    @Override
    public Uni<String> disburse(UUID tenantId, Payout payout) {
        return Uni.createFrom().item("manual:" + payout.id().value());
    }

    @Override
    public Result parseResult(Map<String, String> lowerCaseHeaders, String body, Instant now) {
        throw new BusinessRuleViolation("This payout provider reports results through the operations console");
    }
}
