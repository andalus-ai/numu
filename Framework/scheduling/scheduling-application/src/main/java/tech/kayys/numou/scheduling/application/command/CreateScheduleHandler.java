package tech.kayys.numou.scheduling.application.command;

import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.scheduling.domain.Schedule;
import tech.kayys.numou.scheduling.domain.ScheduleId;
import tech.kayys.numou.scheduling.spi.ScheduleRepository;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class CreateScheduleHandler {

    private final ScheduleRepository scheduleRepository;

    public CreateScheduleHandler(ScheduleRepository scheduleRepository) {
        this.scheduleRepository = Objects.requireNonNull(scheduleRepository);
    }

    public CompletionStage<Result<ScheduleId>> handle(CreateScheduleCommand cmd) {
        ScheduleId id = ScheduleId.generate();
        Schedule schedule = Schedule.create(id, cmd.tenantId(), cmd.name(),
                cmd.startDate(), cmd.endDate());
        return scheduleRepository.save(schedule)
                .thenApply(saved -> Result.success(id));
    }
}
