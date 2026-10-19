package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.payroll.PayrollRun;
import tech.kayys.numou.workforce.domain.payroll.PayrollRunId;
import tech.kayys.numou.workforce.spi.port.PayrollRunRepository;

import java.util.Objects;

public class ApprovePayrollRunHandler implements CommandHandler<ApprovePayrollRunCommand, Result<PayrollRunId>> {

    private final PayrollRunRepository runRepository;
    private final EventPublisher eventPublisher;

    public ApprovePayrollRunHandler(PayrollRunRepository runRepository, EventPublisher eventPublisher) {
        this.runRepository = Objects.requireNonNull(runRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<PayrollRunId>> handle(ApprovePayrollRunCommand cmd) {
        return Uni.createFrom().completionStage(runRepository.findById(cmd.runId()))
                .chain(optRun -> {
                    if (optRun.isEmpty()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("PAYROLL_RUN_NOT_FOUND", "Payroll run not found")));
                    }
                    PayrollRun run = optRun.get();
                    try {
                        run.startCalculation();
                        run.markCalculated();
                        run.approve();
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("PAYROLL_RUN_APPROVAL_FAILED", e.getMessage())));
                    }
                    return Uni.createFrom().completionStage(runRepository.save(run))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}
