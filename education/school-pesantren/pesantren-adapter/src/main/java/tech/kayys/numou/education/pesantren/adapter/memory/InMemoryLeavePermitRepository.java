package tech.kayys.numou.education.pesantren.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.pesantren.domain.LeavePermit;
import tech.kayys.numou.education.pesantren.domain.LeavePermitId;
import tech.kayys.numou.education.pesantren.spi.LeavePermitRepository;

public class InMemoryLeavePermitRepository implements LeavePermitRepository {
    private final Map<LeavePermitId, LeavePermit> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<LeavePermit> save(LeavePermit e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<LeavePermit>> findById(LeavePermitId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(LeavePermitId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(LeavePermit e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(LeavePermitId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
