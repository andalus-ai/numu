package tech.kayys.numou.leave.adapter.memory;

import tech.kayys.numou.leave.domain.LeaveBalance;
import tech.kayys.numou.leave.domain.LeaveBalanceId;
import tech.kayys.numou.leave.spi.LeaveBalanceRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryLeaveBalanceRepository implements LeaveBalanceRepository {
    private final Map<LeaveBalanceId, LeaveBalance> store = new ConcurrentHashMap<>();
    public CompletionStage<LeaveBalance> save(LeaveBalance e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    public CompletionStage<Optional<LeaveBalance>> findById(LeaveBalanceId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    public CompletionStage<Boolean> existsById(LeaveBalanceId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    public CompletionStage<Void> delete(LeaveBalance e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    public CompletionStage<Void> deleteById(LeaveBalanceId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
