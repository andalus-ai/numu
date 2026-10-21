package tech.kayys.numou.education.lms.spi;

import tech.kayys.numou.education.lms.domain.Assignment;
import tech.kayys.numou.education.lms.domain.AssignmentId;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface AssignmentRepository extends Repository<Assignment, AssignmentId> {}
