package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.timesheet.Timesheet;
import tech.kayys.numou.workforce.domain.timesheet.TimesheetId;
import tech.kayys.numou.workforce.spi.port.TimesheetRepository;

import java.util.Objects;

public class ApproveTimesheetHandler implements CommandHandler<ApproveTimesheetCommand, Result<TimesheetId>> {

    private final TimesheetRepository timesheetRepository;
    private final EventPublisher eventPublisher;

    public ApproveTimesheetHandler(TimesheetRepository timesheetRepository, EventPublisher eventPublisher) {
        this.timesheetRepository = Objects.requireNonNull(timesheetRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<TimesheetId>> handle(ApproveTimesheetCommand cmd) {
        return Uni.createFrom().completionStage(timesheetRepository.findById(cmd.timesheetId()))
                .chain(optTimesheet -> {
                    if (optTimesheet.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("TIMESHEET_NOT_FOUND", "Timesheet not found")));
                    }
                    Timesheet timesheet = optTimesheet.get();
                    try {
                        timesheet.approve(cmd.approverActor());
                    } catch (IllegalStateException e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("INVALID_STATUS", e.getMessage())));
                    }

                    return Uni.createFrom().completionStage(timesheetRepository.save(timesheet))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}
