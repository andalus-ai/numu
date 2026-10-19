package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriod;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriodId;
import tech.kayys.numou.workforce.spi.port.PayrollPeriodRepository;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryPayrollPeriodRepository implements PayrollPeriodRepository {

    private final Map<PayrollPeriodId, PayrollPeriod> store = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<PayrollPeriod> save(PayrollPeriod e) {
        store.put(e.getId(), e);
        return CompletableFuture.completedFuture(e);
    }

    @Override
    public CompletionStage<Optional<PayrollPeriod>> findById(PayrollPeriodId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(PayrollPeriodId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(PayrollPeriod e) {
        store.remove(e.getId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(PayrollPeriodId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Optional<PayrollPeriod>> findByPeriod(TenantId tenantId, LocalDate start, LocalDate end) {
        Optional<PayrollPeriod> opt = store.values().stream()
                .filter(p -> p.tenantId().equals(tenantId)
                        && p.startDate().equals(start)
                        && p.endDate().equals(end))
                .findFirst();
        return CompletableFuture.completedFuture(opt);
    }
}
