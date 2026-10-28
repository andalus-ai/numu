package tech.kayys.numou.affiliate.spi;

import tech.kayys.numou.affiliate.domain.PartnerPayout;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Durable payout intent store; adapters must enforce tenant + idempotency-key uniqueness atomically. */
public interface PartnerPayoutRepository {
    CompletionStage<PartnerPayout> saveOrGet(PartnerPayout proposed);

    CompletionStage<Optional<PartnerPayout>> findById(String tenantId, UUID payoutId);

    CompletionStage<Optional<PartnerPayout>> findByIdempotencyKey(String tenantId, String idempotencyKey);

    /** Compare-and-set transition; implementations must reject stale status/version updates. */
    CompletionStage<PartnerPayout> update(String tenantId, UUID payoutId, PartnerPayout replacement);
}
