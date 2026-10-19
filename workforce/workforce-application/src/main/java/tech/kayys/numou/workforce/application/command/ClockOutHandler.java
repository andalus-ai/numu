package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.attendance.AttendanceRecordId;
import tech.kayys.numou.workforce.spi.port.AttendanceRecordRepository;

import java.util.Objects;

public class ClockOutHandler implements CommandHandler<ClockOutCommand, Result<AttendanceRecordId>> {

    private final AttendanceRecordRepository attendanceRepository;
    private final EventPublisher eventPublisher;

    public ClockOutHandler(AttendanceRecordRepository attendanceRepository, EventPublisher eventPublisher) {
        this.attendanceRepository = Objects.requireNonNull(attendanceRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<AttendanceRecordId>> handle(ClockOutCommand cmd) {
        return Uni.createFrom().completionStage(attendanceRepository.findByWorkerAndDate(cmd.workerId(), cmd.employmentId(), cmd.workDate()))
                .chain(optRecord -> {
                    if (optRecord.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("ATTENDANCE_NOT_FOUND", "No attendance record for date")));
                    }
                    var record = optRecord.get();
                    try {
                        record.clockOut(cmd.timestamp());
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("CLOCK_OUT_FAILED", e.getMessage())));
                    }
                    return Uni.createFrom().completionStage(attendanceRepository.save(record))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}
