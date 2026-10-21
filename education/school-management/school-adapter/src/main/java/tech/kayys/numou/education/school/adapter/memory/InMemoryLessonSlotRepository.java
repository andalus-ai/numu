package tech.kayys.numou.education.school.adapter.memory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.school.domain.LessonSlot;
import tech.kayys.numou.education.school.domain.LessonSlotId;
import tech.kayys.numou.education.school.spi.LessonSlotRepository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public class InMemoryLessonSlotRepository implements LessonSlotRepository {
    private final Map<LessonSlotId, LessonSlot> store = new ConcurrentHashMap<>();

    @Override public CompletionStage<LessonSlot> save(LessonSlot e) { store.put(e.getId(), e); return CompletableFuture.completedFuture(e); }
    @Override public CompletionStage<Optional<LessonSlot>> findById(LessonSlotId id) { return CompletableFuture.completedFuture(Optional.ofNullable(store.get(id))); }
    @Override public CompletionStage<Boolean> existsById(LessonSlotId id) { return CompletableFuture.completedFuture(store.containsKey(id)); }
    @Override public CompletionStage<Void> delete(LessonSlot e) { store.remove(e.getId()); return CompletableFuture.completedFuture(null); }
    @Override public CompletionStage<Void> deleteById(LessonSlotId id) { store.remove(id); return CompletableFuture.completedFuture(null); }
    @Override
    public CompletionStage<List<LessonSlot>> findByTeacher(TenantId tenantId, TeacherId teacherId) {
        return CompletableFuture.completedFuture(store.values().stream().filter(s -> s.getTenantId().equals(tenantId) && s.getTeacherId().equals(teacherId)).collect(Collectors.toList()));
    }
    @Override
    public CompletionStage<List<LessonSlot>> findByClassroom(ClassroomId classroomId) {
        return CompletableFuture.completedFuture(store.values().stream().filter(s -> s.getClassroomId().equals(classroomId)).collect(Collectors.toList()));
    }
}
