package tech.kayys.syirkah.construction.application.progress.handler;

import io.smallrye.mutiny.Uni;
import tech.kayys.syirkah.construction.application.progress.command.RecordProgressMeasurementCommand;
import tech.kayys.syirkah.construction.domain.progress.ProgressMeasurement;
import tech.kayys.syirkah.construction.spi.progress.ProgressMeasurementRepository;
import tech.kayys.syirkah.foundation.application.command.CommandHandler;
import tech.kayys.syirkah.foundation.application.event.EventPublisher;
import java.util.Objects;

public final class RecordProgressMeasurementHandler implements CommandHandler<RecordProgressMeasurementCommand, ProgressMeasurement> {
    private final ProgressMeasurementRepository repository;
    private final EventPublisher eventPublisher;

    public RecordProgressMeasurementHandler(ProgressMeasurementRepository repository, EventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    @Override
    public Uni<ProgressMeasurement> handle(RecordProgressMeasurementCommand command) {
        var measurement = ProgressMeasurement.record(
                command.projectId(),
                command.boqId(),
                command.boqItemId(),
                command.measurementDate(),
                command.type(),
                command.quantity(),
                command.remarks()
        );
        return Uni.createFrom()
                .completionStage(repository.save(measurement))
                .call(saved -> eventPublisher.publish(saved.pullDomainEvents()));
    }
}
