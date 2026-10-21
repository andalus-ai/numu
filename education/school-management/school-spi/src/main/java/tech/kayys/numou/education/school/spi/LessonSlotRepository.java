package tech.kayys.numou.education.school.spi;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.school.domain.LessonSlot;
import tech.kayys.numou.education.school.domain.LessonSlotId;
import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public interface LessonSlotRepository extends Repository<LessonSlot, LessonSlotId> {
    CompletionStage<List<LessonSlot>> findByTeacher(TenantId tenantId, TeacherId teacherId);

    CompletionStage<List<LessonSlot>> findByClassroom(ClassroomId classroomId);
}
