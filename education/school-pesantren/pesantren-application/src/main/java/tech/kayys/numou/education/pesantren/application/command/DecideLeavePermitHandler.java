package tech.kayys.numou.education.pesantren.application.command;

import tech.kayys.numou.education.pesantren.domain.LeavePermitId;
import tech.kayys.numou.education.pesantren.spi.LeavePermitRepository;
import tech.kayys.numou.foundation.application.result.Result;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public class DecideLeavePermitHandler {

    private final LeavePermitRepository permits;

    public DecideLeavePermitHandler(LeavePermitRepository permits) {
        this.permits = Objects.requireNonNull(permits);
    }

    public CompletionStage<Result<LeavePermitId>> handle(DecideLeavePermitCommand cmd) {
        return permits.findById(cmd.permitId()).thenCompose(opt -> {
            if (opt.isEmpty() || !opt.get().getTenantId().equals(cmd.tenantId())) {
                return Outcomes.<LeavePermitId>failed("pesantren.leave.not-found", "Leave permit not found");
            }
            var permit = opt.get();
            try {
                if (cmd.approve()) permit.approve(cmd.decidedBy()); else permit.reject(cmd.decidedBy(), cmd.note());
            } catch (IllegalStateException | IllegalArgumentException e) {
                return Outcomes.<LeavePermitId>failed("pesantren.leave.cannot-decide", e.getMessage());
            }
            return permits.save(permit).thenApply(saved -> Result.success(saved.getId()));
        });
    }
}
