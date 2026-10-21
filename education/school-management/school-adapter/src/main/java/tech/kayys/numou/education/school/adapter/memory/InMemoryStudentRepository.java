package tech.kayys.numou.education.school.adapter.memory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import tech.kayys.numou.education.core.Nisn;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.Student;
import tech.kayys.numou.education.school.spi.StudentRepository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public class InMemoryStudentRepository implements StudentRepository {
    private final Map<StudentId, Student> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<Student> save(Student e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<Student>> findById(StudentId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(StudentId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(Student e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(StudentId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<Optional<Student>> findByNisn(TenantId tenantId, Nisn nisn) {
        return CompletableFuture.completedFuture(store.values().stream().filter(s -> s.getTenantId().equals(tenantId) && nisn.equals(s.getNisn())).findFirst());
    }
}
