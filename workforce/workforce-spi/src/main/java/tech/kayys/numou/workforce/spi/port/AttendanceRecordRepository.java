package tech.kayys.numou.workforce.spi.port;

import tech.kayys.numou.foundation.domain.repository.Repository;
import tech.kayys.numou.workforce.domain.attendance.AttendanceRecord;
import tech.kayys.numou.workforce.domain.attendance.AttendanceRecordId;
import tech.kayys.numou.workforce.domain.employment.EmploymentId;
import tech.kayys.numou.workforce.domain.worker.WorkerId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

public interface AttendanceRecordRepository extends Repository<AttendanceRecord, AttendanceRecordId> {

    CompletionStage<Optional<AttendanceRecord>> findByWorkerAndDate(
            WorkerId workerId, EmploymentId employmentId, LocalDate workDate);

    CompletionStage<List<AttendanceRecord>> findByWorkerAndDateRange(
            WorkerId workerId, EmploymentId employmentId, LocalDate startDate, LocalDate endDate);
}
