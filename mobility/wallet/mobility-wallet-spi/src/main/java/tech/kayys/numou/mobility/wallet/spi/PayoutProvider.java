package tech.kayys.numou.mobility.wallet.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.mobility.wallet.domain.Payout;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Bank-transfer disbursement. {@code disburse} must be idempotent on the payout id (it is retried). */
public interface PayoutProvider {

    record Result(UUID tenantId, UUID payoutId, boolean ok, String reason) { }

    /** @return the provider's reference for the transfer */
    Uni<String> disburse(UUID tenantId, Payout payout);

    /** Verifies and parses the provider's result callback. @throws tech.kayys.numou.foundation.domain.exception.BusinessRuleViolation if the signature is invalid */
    Result parseResult(Map<String, String> lowerCaseHeaders, String body, Instant now);
}
