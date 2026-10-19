package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.workforce.domain.payroll.PayrollPeriodId;
import tech.kayys.numou.workforce.domain.payroll.PayrollRun;
import tech.kayys.numou.workforce.domain.payroll.PayrollRunId;
import tech.kayys.numou.workforce.spi.port.PayrollRunRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPayrollRunRepository implements PayrollRunRepository {

    private final Map<PayrollRunId, PayrollRun> store = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<PayrollRun> save(PayrollRun e) {
        store.put(e.getId(), e);
        return CompletableFuture.completedFuture(e);
    }

    @Override
    public CompletionStage<Optional<PayrollRun>> findById(PayrollRunId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(PayrollRunId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(PayrollRun e) {
        store.remove(e.getId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(PayrollRunId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<List<PayrollRun>> findByPeriod(PayrollPeriodId periodId) {
        List<PayrollRun> list = store.values().stream()
                .filter(r -> r.periodId().equals(periodId))
                .toList();
        return CompletableFuture.completedFuture(list);
    }
}
