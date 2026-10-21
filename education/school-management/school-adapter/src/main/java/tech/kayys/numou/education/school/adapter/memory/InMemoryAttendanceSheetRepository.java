package tech.kayys.numou.education.school.adapter.memory;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.school.domain.AttendanceSheet;
import tech.kayys.numou.education.school.domain.AttendanceSheetId;
import tech.kayys.numou.education.school.spi.AttendanceSheetRepository;

public class InMemoryAttendanceSheetRepository implements AttendanceSheetRepository {
    private final Map<AttendanceSheetId, AttendanceSheet> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<AttendanceSheet> save(AttendanceSheet e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<AttendanceSheet>> findById(AttendanceSheetId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(AttendanceSheetId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(AttendanceSheet e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(AttendanceSheetId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<Optional<AttendanceSheet>> findByClassroomAndDate(ClassroomId classroomId, LocalDate date) {
        return CompletableFuture.completedFuture(store.values().stream().filter(s -> s.getClassroomId().equals(classroomId) && s.getDate().equals(date)).findFirst());
    }
}
