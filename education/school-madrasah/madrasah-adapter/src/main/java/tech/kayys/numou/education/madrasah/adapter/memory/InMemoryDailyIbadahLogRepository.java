package tech.kayys.numou.education.madrasah.adapter.memory;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.madrasah.domain.DailyIbadahLog;
import tech.kayys.numou.education.madrasah.domain.IbadahLogId;
import tech.kayys.numou.education.madrasah.spi.DailyIbadahLogRepository;

public class InMemoryDailyIbadahLogRepository implements DailyIbadahLogRepository {
    private final Map<IbadahLogId, DailyIbadahLog> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<DailyIbadahLog> save(DailyIbadahLog e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<DailyIbadahLog>> findById(IbadahLogId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(IbadahLogId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(DailyIbadahLog e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(IbadahLogId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<Optional<DailyIbadahLog>> findByStudentAndDate(StudentId studentId, LocalDate date) {
        return CompletableFuture.completedFuture(store.values().stream().filter(l -> l.getStudentId().equals(studentId) && l.getDate().equals(date)).findFirst());
    }
}
