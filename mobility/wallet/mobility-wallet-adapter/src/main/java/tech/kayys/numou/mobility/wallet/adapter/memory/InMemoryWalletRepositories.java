package tech.kayys.numou.mobility.wallet.adapter.memory;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.aggregate.memory.InMemoryDocumentRepository;
import tech.kayys.numou.mobility.wallet.domain.LedgerEntry;
import tech.kayys.numou.mobility.wallet.domain.Wallet;
import tech.kayys.numou.mobility.wallet.domain.WalletId;
import tech.kayys.numou.mobility.wallet.spi.LedgerRepository;
import tech.kayys.numou.mobility.wallet.spi.WalletRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryWalletRepositories {

    private InMemoryWalletRepositories() { }

    public static class Wallets extends InMemoryDocumentRepository<Wallet, WalletId, Wallet.Snapshot> implements WalletRepository {
        public Wallets() { super(Wallet::snapshot, Wallet::restore); }
    }

    public static class Ledger implements LedgerRepository {
        private record Key(UUID tenant, UUID entry) { }
        private final Map<Key, LedgerEntry> byId = new ConcurrentHashMap<>();
        private final List<UUID[]> order = new CopyOnWriteArrayList<>();

        @Override
        public Uni<Void> append(UUID tenantId, LedgerEntry e) {
            return Uni.createFrom().item(() -> {
                if (byId.putIfAbsent(new Key(tenantId, e.id()), e) == null) {
                    order.add(new UUID[]{tenantId, e.id()});
                }
                return null;
            });
        }

        @Override
        public Uni<List<LedgerEntry>> history(UUID tenantId, WalletId wallet, int limit) {
            return Uni.createFrom().item(() -> byId.entrySet().stream()
                    .filter(e -> e.getKey().tenant().equals(tenantId) && e.getValue().walletId().equals(wallet.value()))
                    .map(Map.Entry::getValue)
                    .sorted(Comparator.comparingLong(LedgerEntry::seq).reversed())
                    .limit(limit).toList());
        }

        @Override
        public Uni<List<LedgerEntry>> range(UUID tenantId, WalletId wallet, java.time.Instant from, java.time.Instant to, int limit) {
            return Uni.createFrom().item(() -> byId.entrySet().stream()
                    .filter(e -> e.getKey().tenant().equals(tenantId) && e.getValue().walletId().equals(wallet.value()))
                    .map(Map.Entry::getValue)
                    .filter(e -> !e.at().isBefore(from) && e.at().isBefore(to))
                    .sorted(Comparator.comparingLong(LedgerEntry::seq))
                    .limit(limit).toList());
        }

        public int size() { return byId.size(); }
    }
}
