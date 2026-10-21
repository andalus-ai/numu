package tech.kayys.numou.education.school.spi;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.core.AcademicYear;
import tech.kayys.numou.education.core.Semester;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.school.domain.ReportCard;
import tech.kayys.numou.education.school.domain.ReportCardId;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface ReportCardRepository extends Repository<ReportCard, ReportCardId> {
    CompletionStage<Optional<ReportCard>> findByStudentAndTerm(StudentId studentId, AcademicYear year, Semester semester);
}
