package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.timesheet.Timesheet;
import tech.kayys.numou.workforce.domain.timesheet.TimesheetId;
import tech.kayys.numou.workforce.spi.port.TimesheetRepository;

import java.util.Objects;

public class CreateTimesheetHandler implements CommandHandler<CreateTimesheetCommand, Result<TimesheetId>> {

    private final TimesheetRepository timesheetRepository;
    private final EventPublisher eventPublisher;

    public CreateTimesheetHandler(TimesheetRepository timesheetRepository, EventPublisher eventPublisher) {
        this.timesheetRepository = Objects.requireNonNull(timesheetRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<TimesheetId>> handle(CreateTimesheetCommand cmd) {
        TimesheetId id = TimesheetId.generate();
        Timesheet timesheet = Timesheet.create(id, cmd.workerId(), cmd.employmentId(), cmd.periodStart(), cmd.periodEnd());
        return Uni.createFrom().completionStage(timesheetRepository.save(timesheet))
                .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                        .replaceWith(Result.success(id)));
    }
}
