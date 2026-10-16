package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.attendance.AttendanceRecord;
import tech.kayys.numou.workforce.domain.attendance.AttendanceRecordId;
import tech.kayys.numou.workforce.domain.worker.WorkerStatus;
import tech.kayys.numou.workforce.spi.port.AttendanceRecordRepository;
import tech.kayys.numou.workforce.spi.port.EmploymentRepository;
import tech.kayys.numou.workforce.spi.port.WorkerRepository;

import java.util.Objects;

public class ClockInHandler implements CommandHandler<ClockInCommand, Result<AttendanceRecordId>> {

    private final WorkerRepository workerRepository;
    private final EmploymentRepository employmentRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final EventPublisher eventPublisher;

    public ClockInHandler(WorkerRepository workerRepository,
                          EmploymentRepository employmentRepository,
                          AttendanceRecordRepository attendanceRepository,
                          EventPublisher eventPublisher) {
        this.workerRepository = Objects.requireNonNull(workerRepository);
        this.employmentRepository = Objects.requireNonNull(employmentRepository);
        this.attendanceRepository = Objects.requireNonNull(attendanceRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<AttendanceRecordId>> handle(ClockInCommand cmd) {
        return Uni.createFrom().completionStage(workerRepository.findById(cmd.workerId()))
                .chain(optWorker -> {
                    if (optWorker.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("WORKER_NOT_FOUND", "Worker not found")));
                    }
                    if (optWorker.get().status() != WorkerStatus.ACTIVE) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("WORKER_NOT_ACTIVE", "Worker is not active")));
                    }
                    return Uni.createFrom().completionStage(attendanceRepository.findByWorkerAndDate(cmd.workerId(), cmd.employmentId(), cmd.workDate()))
                            .chain(optRecord -> {
                                AttendanceRecord record = optRecord.orElseGet(() ->
                                        AttendanceRecord.create(AttendanceRecordId.generate(), cmd.workerId(), cmd.employmentId(), cmd.workDate()));
                                try {
                                    record.clockIn(cmd.timestamp(), cmd.source());
                                } catch (Exception e) {
                                    return Uni.createFrom().item(Result.failure(ApplicationError.of("CLOCK_IN_FAILED", e.getMessage())));
                                }
                                return Uni.createFrom().completionStage(attendanceRepository.save(record))
                                        .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                                .replaceWith(Result.success(saved.getId())));
                            });
                });
    }
}
