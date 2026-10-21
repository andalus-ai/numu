package tech.kayys.numou.education.school.spi;

import tech.kayys.numou.education.core.TeacherId;
import tech.kayys.numou.education.school.domain.Teacher;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface TeacherRepository extends Repository<Teacher, TeacherId> {}
