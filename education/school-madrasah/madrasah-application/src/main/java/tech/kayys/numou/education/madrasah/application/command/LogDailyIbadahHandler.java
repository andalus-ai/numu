package tech.kayys.numou.education.madrasah.application.command;

import tech.kayys.numou.education.madrasah.domain.DailyIbadahLog;
import tech.kayys.numou.education.madrasah.domain.IbadahLogId;
import tech.kayys.numou.education.madrasah.spi.DailyIbadahLogRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Logs ibadah for a student and day; a second call for the same day corrects the existing log. */
public class LogDailyIbadahHandler {

    private final DailyIbadahLogRepository logs;

    public LogDailyIbadahHandler(DailyIbadahLogRepository logs) {
        this.logs = Objects.requireNonNull(logs);
    }

    public CompletionStage<Result<IbadahLogId>> handle(LogDailyIbadahCommand cmd) {
        return logs.findByStudentAndDate(cmd.studentId(), cmd.date()).thenCompose(existing -> {
            DailyIbadahLog log;
            try {
                if (existing.isPresent()) {
                    log = existing.get();
                    log.update(cmd.prayers(), cmd.sunnahFast(), cmd.tilawahPages());
                } else {
                    log = DailyIbadahLog.log(IbadahLogId.generate(), cmd.tenantId(), cmd.studentId(), cmd.date(),
                            cmd.prayers(), cmd.sunnahFast(), cmd.tilawahPages());
                }
            } catch (IllegalArgumentException | NullPointerException e) {
                return Outcomes.<IbadahLogId>failed("madrasah.ibadah.invalid", e.getMessage());
            }
            return logs.save(log).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
