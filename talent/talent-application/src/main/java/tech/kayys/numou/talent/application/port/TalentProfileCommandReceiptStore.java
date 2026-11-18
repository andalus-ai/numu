package tech.kayys.numou.talent.application.port;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.talent.application.api.TalentProfileView;

import java.util.Objects;
import java.util.Optional;

/**
 * Transaction-scoped idempotency receipt contract for Talent commands.
 *
 * <p>{@code claim} must be implemented with a tenant + actor + key unique
 * constraint and conflict-safe insert semantics. A concurrent claimant must
 * wait for the winning transaction and then observe its completed receipt.
 * The claim and {@code complete} must share the aggregate/outbox transaction.</p>
 */
public interface TalentProfileCommandReceiptStore {

    Uni<Claim> claim(
            TenantId tenantId,
            String actorId,
            String idempotencyKey,
            String operation,
            String fingerprint
    );

    Uni<Void> complete(
            TenantId tenantId,
            String actorId,
            String idempotencyKey,
            String fingerprint,
            TalentProfileView result
    );

    record Claim(boolean replay, Optional<TalentProfileView> result) {
        public Claim {
            result = Objects.requireNonNull(result, "result");
            if (replay != result.isPresent()) {
                throw new IllegalArgumentException("A replay claim must contain its completed result");
            }
        }

        public static Claim acquired() {
            return new Claim(false, Optional.empty());
        }

        public static Claim replay(TalentProfileView result) {
            return new Claim(true, Optional.of(Objects.requireNonNull(result, "result")));
        }
    }
}
