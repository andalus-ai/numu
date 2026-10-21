package tech.kayys.numou.education.school.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.core.AcademicYear;
import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.Classroom;
import tech.kayys.numou.education.school.spi.ClassroomRepository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public class InMemoryClassroomRepository implements ClassroomRepository {
    private final Map<ClassroomId, Classroom> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<Classroom> save(Classroom e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<Classroom>> findById(ClassroomId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(ClassroomId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(Classroom e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(ClassroomId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<Optional<Classroom>> findByStudentAndYear(TenantId tenantId, StudentId studentId, AcademicYear year) {
        return CompletableFuture.completedFuture(store.values().stream().filter(c -> c.getTenantId().equals(tenantId) && c.getAcademicYear().equals(year) && c.hasStudent(studentId)).findFirst());
    }
}
