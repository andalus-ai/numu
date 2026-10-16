package tech.kayys.numou.workforce.application.command;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriod;
import tech.kayys.numou.workforce.domain.payroll.PayrollPeriodId;
import tech.kayys.numou.workforce.spi.port.PayrollPeriodRepository;

import java.util.Objects;

public class CreatePayrollPeriodHandler implements CommandHandler<CreatePayrollPeriodCommand, Result<PayrollPeriodId>> {

    private final PayrollPeriodRepository repository;
    private final EventPublisher eventPublisher;

    public CreatePayrollPeriodHandler(PayrollPeriodRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<Result<PayrollPeriodId>> handle(CreatePayrollPeriodCommand cmd) {
        return Uni.createFrom().completionStage(repository.findByPeriod(cmd.tenantId(), cmd.startDate(), cmd.endDate()))
                .chain(existing -> {
                    if (existing.isPresent()) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("PAYROLL_PERIOD_EXISTS", "Payroll period already exists for given dates")));
                    }
                    PayrollPeriod period;
                    try {
                        period = PayrollPeriod.create(
                                PayrollPeriodId.generate(),
                                cmd.tenantId(),
                                cmd.startDate(),
                                cmd.endDate(),
                                cmd.paymentDate()
                        );
                    } catch (Exception e) {
                        return Uni.createFrom().item(Result.failure(ApplicationError.of("INVALID_PAYROLL_PERIOD", e.getMessage())));
                    }
                    return Uni.createFrom().completionStage(repository.save(period))
                            .chain(saved -> eventPublisher.publish(saved.pullDomainEvents())
                                    .replaceWith(Result.success(saved.getId())));
                });
    }
}
