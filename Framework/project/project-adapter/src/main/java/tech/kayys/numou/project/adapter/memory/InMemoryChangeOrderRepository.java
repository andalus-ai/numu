package tech.kayys.numou.project.adapter.memory;

import tech.kayys.numou.project.domain.commercial.ChangeOrder;
import tech.kayys.numou.project.domain.commercial.ChangeOrderId;
import tech.kayys.numou.project.domain.commercial.ProjectContractId;
import tech.kayys.numou.project.domain.project.ProjectId;
import tech.kayys.numou.project.spi.port.ChangeOrderRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryChangeOrderRepository implements ChangeOrderRepository {

    private final Map<ChangeOrderId, ChangeOrder> ordersById = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<ChangeOrder> save(ChangeOrder order) {
        Objects.requireNonNull(order, "order cannot be null");
        ordersById.put(order.id(), order);
        return CompletableFuture.completedFuture(order);
    }

    @Override
    public CompletionStage<Optional<ChangeOrder>> findById(ChangeOrderId id) {
        return CompletableFuture.completedFuture(
                Optional.ofNullable(ordersById.get(id))
        );
    }

    @Override
    public CompletionStage<Optional<ChangeOrder>> findByNumber(ProjectId projectId, String number) {
        Objects.requireNonNull(projectId, "projectId cannot be null");
        Objects.requireNonNull(number, "number cannot be null");

        var found = ordersById.values().stream()
                .filter(o -> o.projectId().equals(projectId) && o.number().equals(number))
                .findFirst();

        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletionStage<List<ChangeOrder>> findByContractId(ProjectContractId contractId) {
        Objects.requireNonNull(contractId, "contractId cannot be null");

        var list = ordersById.values().stream()
                .filter(o -> o.contractId().equals(contractId))
                .toList();

        return CompletableFuture.completedFuture(list);
    }

    @Override
    public CompletionStage<Boolean> existsById(ChangeOrderId id) {
        return CompletableFuture.completedFuture(ordersById.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(ChangeOrder order) {
        Objects.requireNonNull(order, "order cannot be null");
        ordersById.remove(order.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ChangeOrderId id) {
        ordersById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
