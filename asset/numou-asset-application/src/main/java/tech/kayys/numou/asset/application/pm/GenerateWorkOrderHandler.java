package tech.kayys.numou.asset.application.pm;

import io.smallrye.mutiny.Uni;
import tech.kayys.numou.asset.domain.maintenance.due.MaintenanceDue;
import tech.kayys.numou.asset.domain.maintenance.due.MaintenanceDueId;
import tech.kayys.numou.asset.domain.repository.MaintenanceDueRepository;
import tech.kayys.numou.foundation.application.command.Command;
import tech.kayys.numou.foundation.application.command.CommandHandler;
import tech.kayys.numou.foundation.application.event.EventPublisher;
import tech.kayys.numou.foundation.application.result.ApplicationError;
import tech.kayys.numou.foundation.application.result.ApplicationErrorException;
import tech.kayys.numou.foundation.application.result.Result;
import tech.kayys.numou.foundation.application.transaction.UnitOfWork;
import tech.kayys.numou.foundation.domain.time.DomainClock;

import java.util.Objects;
import java.util.UUID;

/**
 * Idempotent preventive work-order generation (ASSET-22).
 *
 * <p><strong>Decision (WO-generation path):</strong> the ASSET-19
 * {@code domain/maintenance/workorder/} aggregate does not exist in this workspace yet
 * (verified: no workorder sources under the asset module), and this milestone is
 * CREATE-ONLY scoped, so the handler does <em>not</em> construct a foreign aggregate.
 * Instead it records the generation marker on the due occurrence
 * ({@link MaintenanceDue#markWorkOrderGenerated}) and emits
 * {@code maintenance.preventive-work-order-generated} via the outbox. The ASSET-19
 * owner consumes that event to build the real work order; retries reuse the recorded
 * {@code workOrderId}, keeping generation idempotent.</p>
 */
public class GenerateWorkOrderHandler extends AbstractPmCommandHandler
        implements CommandHandler<GenerateWorkOrderHandler.GenerateWorkOrderCommand, Result<String>> {

    public record GenerateWorkOrderCommand(String tenantId, String dueId) implements Command {}

    private final MaintenanceDueRepository dues;

    public GenerateWorkOrderHandler(MaintenanceDueRepository dues, EventPublisher eventPublisher,
                                    UnitOfWork unitOfWork, DomainClock clock) {
        super(eventPublisher, unitOfWork, clock);
        this.dues = Objects.requireNonNull(dues, "dues");
    }

    @Override
    public Uni<Result<String>> handle(GenerateWorkOrderCommand command) {
        MaintenanceDueId dueId = MaintenanceDueId.of(UUID.fromString(command.dueId()));
        return Uni.createFrom().completionStage(() -> dues.findByTenantAndId(command.tenantId(), dueId))
                .flatMap(opt -> {
                    MaintenanceDue due = opt.orElseThrow(() -> new ApplicationErrorException(
                            ApplicationError.of("maintenance.due.not-found", "Due not found: " + command.dueId())));
                    if (due.isClosed()) {
                        return Uni.createFrom().item(Result.<String>failure(ApplicationError.of(
                                "maintenance.due.closed", "Due is closed: " + due.status())));
                    }
                    if (due.workOrderId() != null) {
                        return Uni.createFrom().item(Result.success(due.workOrderId().toString()));
                    }
                    UUID generated = due.markWorkOrderGenerated(null, clock);
                    return saveAndPublish(dues.save(due), due)
                            .map(saved -> Result.success(generated.toString()));
                });
    }
}
