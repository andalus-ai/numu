package tech.kayys.numou.education.madrasah.spi;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.core.StudentId;
import tech.kayys.numou.education.madrasah.domain.DailyIbadahLog;
import tech.kayys.numou.education.madrasah.domain.IbadahLogId;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface DailyIbadahLogRepository extends Repository<DailyIbadahLog, IbadahLogId> {
    CompletionStage<Optional<DailyIbadahLog>> findByStudentAndDate(StudentId studentId, LocalDate date);
}
