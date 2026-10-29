package tech.kayys.numou.pos.application.port.out;

import tech.kayys.numou.pos.application.port.out.SaleRepository.VersionedSale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Durable idempotent command boundary for POS sale mutations.
 *
 * <p>Implementations must atomically bind (tenant, actor, key) to operation
 * and request fingerprint, serialize concurrent attempts for the same key,
 * and persist the completed result. Replays with the same fingerprint return
 * the original result; a different fingerprint for the same key is rejected.
 * The command effect and completed idempotency result must share a transaction
 * or an equivalent recoverable protocol.</p>
 */
@FunctionalInterface
public interface SaleCommandIdempotency {
    VersionedSale execute(
            UUID tenantId,
            UUID actorId,
            String idempotencyKey,
            String operation,
            String requestFingerprint,
            Supplier<VersionedSale> command);
}
