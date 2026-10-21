package tech.kayys.numou.education.pesantren.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.pesantren.domain.SantriId;
import tech.kayys.numou.education.pesantren.domain.SantriWallet;
import tech.kayys.numou.education.pesantren.domain.SantriWalletId;
import tech.kayys.numou.education.pesantren.spi.SantriWalletRepository;

public class InMemorySantriWalletRepository implements SantriWalletRepository {
    private final Map<SantriWalletId, SantriWallet> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<SantriWallet> save(SantriWallet e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<SantriWallet>> findById(SantriWalletId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(SantriWalletId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(SantriWallet e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(SantriWalletId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<Optional<SantriWallet>> findBySantri(SantriId santriId) {
        return CompletableFuture.completedFuture(store.values().stream().filter(w -> w.getSantriId().equals(santriId)).findFirst());
    }
}
