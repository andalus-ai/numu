package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.timesheet.Timesheet;
import tech.kayys.numou.workforce.domain.timesheet.TimesheetId;
import tech.kayys.numou.workforce.domain.timesheet.TimesheetStatus;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.spi.port.TimesheetRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryTimesheetRepository implements TimesheetRepository {

    private final Map<TimesheetId, Timesheet> store = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Timesheet> save(Timesheet e) {
        store.put(e.getId(), e);
        return CompletableFuture.completedFuture(e);
    }

    @Override
    public CompletionStage<Optional<Timesheet>> findById(TimesheetId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(TimesheetId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(Timesheet e) {
        store.remove(e.getId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(TimesheetId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Optional<Timesheet>> findByWorkerAndPeriod(
            WorkerId workerId, EmploymentId employmentId, LocalDate periodStart, LocalDate periodEnd) {
        return CompletableFuture.completedFuture(
                store.values().stream()
                        .filter(t -> t.getWorkerId().equals(workerId)
                                && t.getEmploymentId().equals(employmentId)
                                && t.getPeriodStart().equals(periodStart)
                                && t.getPeriodEnd().equals(periodEnd))
                        .findFirst()
        );
    }

    @Override
    public CompletionStage<List<Timesheet>> findByWorkerId(WorkerId workerId) {
        return CompletableFuture.completedFuture(
                store.values().stream()
                        .filter(t -> t.getWorkerId().equals(workerId))
                        .toList()
        );
    }

    @Override
    public CompletionStage<List<Timesheet>> findByStatus(TimesheetStatus status) {
        return CompletableFuture.completedFuture(
                store.values().stream()
                        .filter(t -> t.getStatus() == status)
                        .toList()
        );
    }
}
