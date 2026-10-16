package tech.kayys.syirkah.project.application.support;

import tech.kayys.syirkah.project.domain.commercial.ChangeOrder;
import tech.kayys.syirkah.project.domain.commercial.ChangeOrderId;
import tech.kayys.syirkah.project.domain.commercial.ProjectContractId;
import tech.kayys.syirkah.project.domain.project.ProjectId;
import tech.kayys.syirkah.project.spi.port.ChangeOrderRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class InMemoryChangeOrderRepository implements ChangeOrderRepository {

    private final Map<ChangeOrderId, ChangeOrder> ordersById = new LinkedHashMap<>();

    @Override
    public CompletionStage<ChangeOrder> save(ChangeOrder order) {
        ordersById.put(order.id(), order);
        return CompletableFuture.completedFuture(order);
    }

    @Override
    public CompletionStage<Optional<ChangeOrder>> findById(ChangeOrderId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(ordersById.get(id)));
    }

    @Override
    public CompletionStage<Optional<ChangeOrder>> findByNumber(ProjectId projectId, String number) {
        var found = ordersById.values().stream()
                .filter(o -> o.projectId().equals(projectId) && o.number().equals(number))
                .findFirst();
        return CompletableFuture.completedFuture(found);
    }

    @Override
    public CompletionStage<List<ChangeOrder>> findByContractId(ProjectContractId contractId) {
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
        ordersById.remove(order.id());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(ChangeOrderId id) {
        ordersById.remove(id);
        return CompletableFuture.completedFuture(null);
    }
}
