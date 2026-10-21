package tech.kayys.numou.education.lms.spi;

import tech.kayys.numou.education.lms.domain.Course;
import tech.kayys.numou.education.lms.domain.CourseId;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface CourseRepository extends Repository<Course, CourseId> {}
