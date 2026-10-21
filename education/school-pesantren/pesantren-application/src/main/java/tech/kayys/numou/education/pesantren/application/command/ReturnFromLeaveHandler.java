package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.spi.LeavePermitRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** Closes an approved permit when the santri returns; the success value is the number of days late (0 if on time). */
public class ReturnFromLeaveHandler {

    private final LeavePermitRepository permits;

    public ReturnFromLeaveHandler(LeavePermitRepository permits) {
        this.permits = Objects.requireNonNull(permits);
    }

    public CompletionStage<Result<Integer>> handle(ReturnFromLeaveCommand cmd) {
        return permits.findById(cmd.permitId()).thenCompose(opt -> {
            if (opt.isEmpty() || !opt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<Integer>failed("pesantren.leave.not-found", "Leave permit not found");
            }
            var permit = opt.get();
            int lateDays;
            try {
                lateDays = permit.markReturned(cmd.returnedOn());
            } catch (IllegalStateException | IllegalArgumentException | NullPointerException e) {
                return Outcomes.<Integer>failed("pesantren.leave.cannot-return", e.getMessage());
            }
            return permits.save(permit).thenApply(saved -> Result.success(lateDays));
        });
    }
}
