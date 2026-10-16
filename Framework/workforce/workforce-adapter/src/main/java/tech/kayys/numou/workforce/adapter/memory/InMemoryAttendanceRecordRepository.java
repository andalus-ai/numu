package tech.kayys.numou.workforce.adapter.memory;

import tech.kayys.numou.workforce.domain.attendance.AttendanceRecord;
import tech.kayys.numou.workforce.domain.attendance.AttendanceRecordId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;
import tech.kayys.numou.workforce.spi.port.AttendanceRecordRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAttendanceRecordRepository implements AttendanceRecordRepository {

    private final Map<AttendanceRecordId, AttendanceRecord> store = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<AttendanceRecord> save(AttendanceRecord e) {
        store.put(e.getId(), e);
        return CompletableFuture.completedFuture(e);
    }

    @Override
    public CompletionStage<Optional<AttendanceRecord>> findById(AttendanceRecordId id) {
        return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id)));
    }

    @Override
    public CompletionStage<Boolean> existsById(AttendanceRecordId id) {
        return CompletableFuture.completedFuture(store.containsKey(id));
    }

    @Override
    public CompletionStage<Void> delete(AttendanceRecord e) {
        store.remove(e.getId());
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Void> deleteById(AttendanceRecordId id) {
        store.remove(id);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Optional<AttendanceRecord>> findByWorkerAndDate(
            WorkerId workerId, EmploymentId employmentId, LocalDate workDate) {
        return CompletableFuture.completedFuture(
                store.values().stream()
                        .filter(r -> r.getWorkerId().equals(workerId)
                                && r.getEmploymentId().equals(employmentId)
                                && r.getWorkDate().equals(workDate))
                        .findFirst()
        );
    }

    @Override
    public CompletionStage<List<AttendanceRecord>> findByWorkerAndDateRange(
            WorkerId workerId, EmploymentId employmentId, LocalDate startDate, LocalDate endDate) {
        return CompletableFuture.completedFuture(
                store.values().stream()
                        .filter(r -> r.getWorkerId().equals(workerId)
                                && r.getEmploymentId().equals(employmentId)
                                && !r.getWorkDate().isBefore(startDate)
                                && !r.getWorkDate().isAfter(endDate))
                        .toList()
        );
    }
}
