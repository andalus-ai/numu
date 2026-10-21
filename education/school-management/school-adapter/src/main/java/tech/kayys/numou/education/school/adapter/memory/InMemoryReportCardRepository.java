package tech.kayys.numou.education.school.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.core.AcademicYear;
import tech.kayys.numou.education.core.Semester;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.ReportCard;
import tech.kayys.numou.education.school.domain.ReportCardId;
import tech.kayys.numou.education.school.spi.ReportCardRepository;

public class InMemoryReportCardRepository implements ReportCardRepository {
    private final Map<ReportCardId, ReportCard> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<ReportCard> save(ReportCard e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<ReportCard>> findById(ReportCardId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(ReportCardId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(ReportCard e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(ReportCardId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<Optional<ReportCard>> findByStudentAndTerm(StudentId studentId, AcademicYear year, Semester semester) {
        return CompletableFuture.completedFuture(store.values().stream().filter(r -> r.getStudentId().equals(studentId) && r.getAcademicYear().equals(year) && r.getSemester() == semester).findFirst());
    }
}
