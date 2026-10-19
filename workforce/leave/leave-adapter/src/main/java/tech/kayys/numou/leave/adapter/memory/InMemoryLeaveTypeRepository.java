package tech.kayys.numou.leave.adapter.memory;

import tech.kayys.numou.leave.domain.LeaveType;
import tech.kayys.numou.leave.domain.LeaveTypeId;
import tech.kayys.numou.leave.spi.LeaveTypeRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryLeaveTypeRepository implements LeaveTypeRepository {
    private final Map<LeaveTypeId, LeaveType> store = new ConcurrentHashMap<>();
    public CompletionStage<LeaveType> save(LeaveType e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    public CompletionStage<Optional<LeaveType>> findById(LeaveTypeId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    public CompletionStage<Boolean> existsById(LeaveTypeId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    public CompletionStage<Void> delete(LeaveType e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    public CompletionStage<Void> deleteById(LeaveTypeId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
