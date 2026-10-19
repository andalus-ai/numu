package tech.kayys.numou.leave.adapter.memory;

import tech.kayys.numou.leave.domain.LeaveRequest;
import tech.kayys.numou.leave.domain.LeaveRequestId;
import tech.kayys.numou.leave.spi.LeaveRequestRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryLeaveRequestRepository implements LeaveRequestRepository {
    private final Map<LeaveRequestId, LeaveRequest> store = new ConcurrentHashMap<>();
    public CompletionStage<LeaveRequest> save(LeaveRequest e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    public CompletionStage<Optional<LeaveRequest>> findById(LeaveRequestId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    public CompletionStage<Boolean> existsById(LeaveRequestId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    public CompletionStage<Void> delete(LeaveRequest e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    public CompletionStage<Void> deleteById(LeaveRequestId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
}
