package tech.kayys.numou.crm.application.support;

import tech.kayys.numou.crm.domain.referral.Referral;
import tech.kayys.numou.crm.domain.referral.ReferralId;
import tech.kayys.numou.crm.domain.repository.ReferralRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * In-memory implementation of {@link ReferralRepository} for testing.
 */
public class InMemoryReferralRepository implements ReferralRepository {

    private final Map<ReferralId, Referral> store = new HashMap<>();

    @Override
    public CompletionStage<Referral> save(Referral referral) {
        Objects.requireNonNull(referral, "referral cannot be null");
        store.put(referral.id(), referral);
        return CompletableFuture.completedFuture(referral);
    }

    @Override
    public CompletionStage<Optional<Referral>> findById(ReferralId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(ReferralId id) {
        Objects.requireNonNull(id, "id cannot be null");
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Referral referral) {
        Objects.requireNonNull(referral, "referral cannot be null");
        store.remove(referral.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ReferralId id) {
        Objects.requireNonNull(id, "id cannot be null");
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}