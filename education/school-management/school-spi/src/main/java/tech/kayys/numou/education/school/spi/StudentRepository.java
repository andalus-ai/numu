package tech.kayys.numou.education.school.spi;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.core.Nisn;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.Student;
import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.foundation.domain.tenant.TenantId;

public interface StudentRepository extends Repository<Student, StudentId> {
    CompletionStage<Optional<Student>> findByNisn(TenantId tenantId, Nisn nisn);
}
