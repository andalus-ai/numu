package tech.kayys.numou.education.school.spi;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import tech.kayys.numou.education.core.ClassroomId;
import tech.kayys.numou.education.school.domain.AttendanceSheet;
import tech.kayys.numou.education.school.domain.AttendanceSheetId;
import tech.kayys.numou.foundation.domain.repository.Repository;

public interface AttendanceSheetRepository extends Repository<AttendanceSheet, AttendanceSheetId> {
    CompletionStage<Optional<AttendanceSheet>> findByClassroomAndDate(ClassroomId classroomId, LocalDate date);
}
