package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.commercial.ChangeRequest;
import tech.kayys.numou.project.domain.commercial.ChangeRequestId;
import tech.kayys.numou.project.domain.commercial.ProjectContractId;
import tech.kayys.numou.project.spi.port.ChangeRequestRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryChangeRequestRepository implements ChangeRequestRepository {

    private final Map<ChangeRequestId, ChangeRequest> requestsById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ChangeRequest> save(ChangeRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        requestsById.put(request.id(), request);
        return CompletableFuture.completedFuture(request);
    }

    @Override
    public CompletionStage<Optional<ChangeRequest>> findById(ChangeRequestId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(requestsById.get(id))
        );
    }

    @Override
    public CompletionStage<List<ChangeRequest>> findByContractId(ProjectContractId contractId) {
        Objects.requireNonNull(contractId, "contractId cannot be null");

        var list = requestsById.values().stream()
                .filter(r -> r.contractId().equals(contractId))
                .toList();

        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletionStage<Boolean> existsById(ChangeRequestId id) {
        return CompletableFuture.completedFuture(requestsById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(ChangeRequest request) {
        Objects.requireNonNull(request, "request cannot be null");
        requestsById.remove(request.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ChangeRequestId id) {
        requestsById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
