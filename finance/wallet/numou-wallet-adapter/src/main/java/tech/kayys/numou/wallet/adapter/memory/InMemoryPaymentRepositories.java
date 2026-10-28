package tech.kayys.numou.wallet.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.memory.InMemoryDocumentRepository;
import tech.kayys.numou.wallet.domain.Payout;
import tech.kayys.numou.wallet.domain.PayoutId;
import tech.kayys.numou.wallet.domain.TopUpId;
import tech.kayys.numou.wallet.domain.TopUpIntent;
import tech.kayys.numou.wallet.spi.PaymentRepositories;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class InMemoryPaymentRepositories {

    private InMemoryPaymentRepositories() { }

    public static class TopUps extends InMemoryDocumentRepository<TopUpIntent, TopUpId, TopUpIntent.Snapshot> implements PaymentRepositories.TopUps {
        public TopUps() { super(TopUpIntent::snapshot, TopUpIntent::restore); }
    }

    public static class Payouts extends InMemoryDocumentRepository<Payout, PayoutId, Payout.Snapshot> implements PaymentRepositories.Payouts {
        public Payouts() { super(Payout::snapshot, Payout::restore); }

        @Override
        public Uni<List<Payout>> findByStatus(UUID tenantId, Payout.Status status, int limit) {
            return Uni.createFrom().item(() -> allOf(tenantId).stream().filter(p -> p.status() == status)
                    .sorted(Comparator.comparing(Payout::getCreatedAt)).limit(limit).toList());
        }
    }
}
