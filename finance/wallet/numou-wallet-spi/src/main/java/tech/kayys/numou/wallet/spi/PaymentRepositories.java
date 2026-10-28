package tech.kayys.numou.wallet.spi;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.spi.VersionedRepository;
import tech.kayys.numou.wallet.domain.Payout;
import tech.kayys.numou.wallet.domain.PayoutId;
import tech.kayys.numou.wallet.domain.TopUpId;
import tech.kayys.numou.wallet.domain.TopUpIntent;

import java.util.List;
import java.util.UUID;

public final class PaymentRepositories {

    private PaymentRepositories() { }

    public interface TopUps extends VersionedRepository<TopUpIntent, TopUpId> { }

    public interface Payouts extends VersionedRepository<Payout, PayoutId> {
        /** Oldest first. */
        Uni<List<Payout>> findByStatus(UUID tenantId, Payout.Status status, int limit);
    }
}
