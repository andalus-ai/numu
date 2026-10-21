package tech.kayys.numou.education.school.spi;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.core.AcademicYear;
import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.Classroom;
import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public interface ClassroomRepository extends Repository<Classroom, ClassroomId> {
    CompletionStage<Optional<Classroom>> findByStudentAndYear(TenantId tenantId, StudentId studentId, AcademicYear year);
}
